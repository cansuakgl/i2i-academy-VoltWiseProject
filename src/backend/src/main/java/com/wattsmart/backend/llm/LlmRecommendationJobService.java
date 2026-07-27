package com.wattsmart.backend.llm;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmRecommendationJobService {

    private static final String SYSTEM_PROMPT = """
            Sen WattSmart uygulamasının enerji danışmanısın.
            Yanıtını yalnızca Türkçe yaz.
            Kullanıcıya doğrudan, sade, nazik ve uygulanabilir öneriler ver.
            Teknik metrikleri açıklarken kısa cümleler kullan.
            Korkutucu veya kesin olmayan iddialar yazma.
            Yanıt 3-6 kısa maddeden oluşsun ve e-postada doğrudan kullanılabilir olsun.
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final GeminiClient geminiClient;

    @Transactional
    public int generateMonthlyRecommendations() {
        return processCandidates(findMonthlyCandidates());
    }

    @Transactional
    public int generateUrgentRecommendations() {
        return processCandidates(findMilestoneCandidates()) + processCandidates(findAnomalyCandidates());
    }

    private int processCandidates(List<RecommendationCandidate> candidates) {
        int processed = 0;
        for (RecommendationCandidate candidate : candidates) {
            String responseText = generateText(candidate);
            UUID recommendationId = insertRecommendation(candidate, responseText);
            if (recommendationId != null) {
                insertEmailNotification(candidate, recommendationId, responseText);
                if ("ANOMALY_ALERT".equals(candidate.triggerType())) {
                    markAnomalyQueued(candidate.sourceAnomalyId());
                }
                processed++;
            }
        }
        return processed;
    }

    private String generateText(RecommendationCandidate candidate) {
        try {
            return geminiClient.generateRecommendation(SYSTEM_PROMPT, candidate.prompt());
        } catch (RuntimeException exception) {
            log.warn("LLM recommendation generation failed. triggerType={}, homeId={}, userId={}, message={}",
                    candidate.triggerType(),
                    candidate.homeId(),
                    candidate.userId(),
                    exception.getMessage(),
                    exception);
            return fallbackText(candidate);
        }
    }

    private List<RecommendationCandidate> findMonthlyCandidates() {
        return jdbcTemplate.query("""
                        SELECT
                            hls.id::TEXT AS home_llm_summary_id,
                            hls.home_id::TEXT AS home_id,
                            h.name AS home_name,
                            hls.summary_text,
                            hls.period_start::TEXT AS period_start,
                            hls.period_end::TEXT AS period_end,
                            hum.user_id::TEXT AS user_id,
                            u.email AS recipient_email,
                            u.first_name AS first_name
                        FROM wattsmart.home_llm_summaries hls
                        JOIN wattsmart.homes h
                            ON h.id = hls.home_id
                        JOIN wattsmart.home_user_memberships hum
                            ON hum.home_id = hls.home_id
                           AND hum.accepted_at IS NOT NULL
                        JOIN wattsmart.users u
                            ON u.id = hum.user_id
                        JOIN wattsmart.user_notification_preferences unp
                            ON unp.user_id = hum.user_id
                           AND unp.email_enabled = TRUE
                           AND unp.monthly_summary_enabled = TRUE
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM wattsmart.llm_recommendations lr
                            WHERE lr.home_llm_summary_id = hls.id
                              AND lr.user_id = hum.user_id
                              AND lr.trigger_type = 'MONTHLY_SUMMARY'
                        )
                        ORDER BY hls.period_start DESC, h.name ASC
                        LIMIT 50
                        """,
                (rs, rowNum) -> {
                    String prompt = """
                            Aylık enerji özeti için kullanıcıya öneri hazırla.
                            Ev: %s
                            Kullanıcı: %s
                            Dönem: %s - %s
                            Özet: %s
                            """.formatted(
                            rs.getString("home_name"),
                            rs.getString("first_name"),
                            rs.getString("period_start"),
                            rs.getString("period_end"),
                            rs.getString("summary_text"));
                    return new RecommendationCandidate(
                            "MONTHLY_SUMMARY",
                            UUID.fromString(rs.getString("home_id")),
                            UUID.fromString(rs.getString("user_id")),
                            rs.getString("recipient_email"),
                            rs.getString("home_name"),
                            UUID.fromString(rs.getString("home_llm_summary_id")),
                            null,
                            null,
                            null,
                            "Aylık enerji önerileri - " + rs.getString("home_name"),
                            prompt);
                });
    }

    private List<RecommendationCandidate> findMilestoneCandidates() {
        return jdbcTemplate.query("""
                        SELECT
                            hme.id::TEXT AS milestone_event_id,
                            hme.home_id::TEXT AS home_id,
                            h.name AS home_name,
                            hme.usage_date::TEXT AS usage_date,
                            hme.milestone::TEXT AS milestone,
                            hme.stage::TEXT AS milestone_stage,
                            hme.usage_percentage_of_limit,
                            hum.user_id::TEXT AS user_id,
                            u.email AS recipient_email,
                            u.first_name AS first_name
                        FROM wattsmart.home_milestone_events hme
                        JOIN wattsmart.homes h
                            ON h.id = hme.home_id
                        JOIN wattsmart.home_user_memberships hum
                            ON hum.home_id = hme.home_id
                           AND hum.accepted_at IS NOT NULL
                        JOIN wattsmart.users u
                            ON u.id = hum.user_id
                        JOIN wattsmart.user_notification_preferences unp
                            ON unp.user_id = hum.user_id
                           AND unp.email_enabled = TRUE
                           AND unp.usage_milestone_enabled = TRUE
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM wattsmart.llm_recommendations lr
                            WHERE lr.source_milestone_event_id = hme.id
                              AND lr.user_id = hum.user_id
                              AND lr.trigger_type = 'USAGE_MILESTONE'
                        )
                        ORDER BY hme.triggered_at ASC
                        LIMIT 50
                        """,
                (rs, rowNum) -> {
                    String prompt = """
                            Kullanıcı kullanım kotası kilometre taşına ulaştı. Davranışsal enerji tasarrufu önerisi hazırla.
                            Ev: %s
                            Kullanıcı: %s
                            Tarih: %s
                            Eşik: %s
                            Aşama: %s
                            Kullanım yüzdesi: %s
                            """.formatted(
                            rs.getString("home_name"),
                            rs.getString("first_name"),
                            rs.getString("usage_date"),
                            rs.getString("milestone"),
                            rs.getString("milestone_stage"),
                            rs.getString("usage_percentage_of_limit"));
                    return new RecommendationCandidate(
                            "USAGE_MILESTONE",
                            UUID.fromString(rs.getString("home_id")),
                            UUID.fromString(rs.getString("user_id")),
                            rs.getString("recipient_email"),
                            rs.getString("home_name"),
                            null,
                            UUID.fromString(rs.getString("milestone_event_id")),
                            null,
                            rs.getString("usage_date"),
                            "Enerji kullanım uyarısı - " + rs.getString("home_name"),
                            prompt);
                });
    }

    private List<RecommendationCandidate> findAnomalyCandidates() {
        return jdbcTemplate.query("""
                        SELECT
                            aa.id::TEXT AS anomaly_id,
                            aa.home_id::TEXT AS home_id,
                            h.name AS home_name,
                            a.name AS appliance_name,
                            a.appliance_code,
                            aa.started_at,
                            aa.breached_safe_watt_limit,
                            aa.peak_watts,
                            hum.user_id::TEXT AS user_id,
                            u.email AS recipient_email,
                            u.first_name AS first_name
                        FROM wattsmart.appliance_anomalies aa
                        JOIN wattsmart.homes h
                            ON h.id = aa.home_id
                        JOIN wattsmart.appliances a
                            ON a.id = aa.appliance_id
                        JOIN wattsmart.home_user_memberships hum
                            ON hum.home_id = aa.home_id
                           AND hum.accepted_at IS NOT NULL
                        JOIN wattsmart.users u
                            ON u.id = hum.user_id
                        JOIN wattsmart.user_notification_preferences unp
                            ON unp.user_id = hum.user_id
                           AND unp.email_enabled = TRUE
                           AND unp.anomaly_alert_enabled = TRUE
                        WHERE aa.status = 'OPEN'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM wattsmart.llm_recommendations lr
                              WHERE lr.source_anomaly_id = aa.id
                                AND lr.user_id = hum.user_id
                                AND lr.trigger_type = 'ANOMALY_ALERT'
                          )
                        ORDER BY aa.started_at ASC
                        LIMIT 50
                        """,
                (rs, rowNum) -> {
                    String prompt = """
                            Bir cihaz güvenli watt sınırını aşarak anomalili görünüyor. Kullanıcıya sakin ve pratik öneriler hazırla.
                            Ev: %s
                            Kullanıcı: %s
                            Cihaz: %s (%s)
                            Başlangıç: %s
                            Güvenli watt sınırı: %s
                            Tepe watt: %s
                            """.formatted(
                            rs.getString("home_name"),
                            rs.getString("first_name"),
                            rs.getString("appliance_name"),
                            rs.getString("appliance_code"),
                            rs.getObject("started_at", OffsetDateTime.class),
                            rs.getString("breached_safe_watt_limit"),
                            rs.getString("peak_watts"));
                    return new RecommendationCandidate(
                            "ANOMALY_ALERT",
                            UUID.fromString(rs.getString("home_id")),
                            UUID.fromString(rs.getString("user_id")),
                            rs.getString("recipient_email"),
                            rs.getString("home_name"),
                            null,
                            null,
                            UUID.fromString(rs.getString("anomaly_id")),
                            null,
                            "Cihaz tüketim anomalisi - " + rs.getString("home_name"),
                            prompt);
                });
    }

    private UUID insertRecommendation(RecommendationCandidate candidate, String responseText) {
        return jdbcTemplate.query("""
                        INSERT INTO wattsmart.llm_recommendations (
                            home_id,
                            user_id,
                            home_llm_summary_id,
                            source_milestone_event_id,
                            source_anomaly_id,
                            trigger_type,
                            status,
                            recipient_email,
                            language_code,
                            prompt_snapshot,
                            response_text
                        )
                        VALUES (
                            :homeId,
                            :userId,
                            :homeLlmSummaryId,
                            :sourceMilestoneEventId,
                            :sourceAnomalyId,
                            CAST(:triggerType AS wattsmart.llm_recommendation_trigger),
                            'GENERATED',
                            :recipientEmail,
                            'tr',
                            :promptSnapshot,
                            :responseText
                        )
                        ON CONFLICT DO NOTHING
                        RETURNING id::TEXT
                        """,
                parametersFor(candidate)
                        .addValue("responseText", responseText),
                rs -> rs.next() ? UUID.fromString(rs.getString("id")) : null);
    }

    private void insertEmailNotification(RecommendationCandidate candidate, UUID recommendationId, String responseText) {
        jdbcTemplate.update("""
                        INSERT INTO wattsmart.email_notifications (
                            home_id,
                            user_id,
                            llm_recommendation_id,
                            home_llm_summary_id,
                            notification_type,
                            status,
                            source_usage_date,
                            recipient_email,
                            subject_text,
                            body_text
                        )
                        VALUES (
                            :homeId,
                            :userId,
                            :recommendationId,
                            :homeLlmSummaryId,
                            'LLM_RECOMMENDATION',
                            'PENDING',
                            CAST(:sourceUsageDate AS DATE),
                            :recipientEmail,
                            :subjectText,
                            :bodyText
                        )
                        """,
                parametersFor(candidate)
                        .addValue("recommendationId", recommendationId)
                        .addValue("bodyText", responseText));
    }

    private void markAnomalyQueued(UUID anomalyId) {
        jdbcTemplate.update("""
                        UPDATE wattsmart.appliance_anomalies
                        SET notification_sent_at = COALESCE(notification_sent_at, NOW())
                        WHERE id = :anomalyId
                        """,
                new MapSqlParameterSource("anomalyId", anomalyId));
    }

    private MapSqlParameterSource parametersFor(RecommendationCandidate candidate) {
        return new MapSqlParameterSource()
                .addValue("homeId", candidate.homeId())
                .addValue("userId", candidate.userId())
                .addValue("homeLlmSummaryId", candidate.homeLlmSummaryId())
                .addValue("sourceMilestoneEventId", candidate.sourceMilestoneEventId())
                .addValue("sourceAnomalyId", candidate.sourceAnomalyId())
                .addValue("triggerType", candidate.triggerType())
                .addValue("recipientEmail", candidate.recipientEmail())
                .addValue("promptSnapshot", candidate.prompt())
                .addValue("sourceUsageDate", candidate.sourceUsageDate())
                .addValue("subjectText", candidate.subjectText());
    }

    private String fallbackText(RecommendationCandidate candidate) {
        return """
                Merhaba,
                %s için enerji kullanımınızda dikkat gerektiren bir durum tespit edildi.
                Lütfen yüksek tüketimli cihazları kısa süreli kullanmaya, gereksiz bekleme modlarını kapatmaya ve yoğun tüketimi mümkünse daha uygun saatlere kaydırmaya çalışın.
                Bu öneri otomatik yedek metin olarak oluşturuldu; sistem kısa süre içinde daha ayrıntılı öneriler üretmeyi tekrar deneyecektir.
                """.formatted(candidate.homeName()).trim();
    }

    private record RecommendationCandidate(
            String triggerType,
            UUID homeId,
            UUID userId,
            String recipientEmail,
            String homeName,
            UUID homeLlmSummaryId,
            UUID sourceMilestoneEventId,
            UUID sourceAnomalyId,
            String sourceUsageDate,
            String subjectText,
            String prompt
    ) {
    }
}
