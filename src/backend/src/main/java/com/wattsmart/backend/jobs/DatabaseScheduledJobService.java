package com.wattsmart.backend.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wattsmart.backend.llm.LlmRecommendationJobService;
import com.wattsmart.backend.notifications.EmailNotificationDispatchService;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseScheduledJobService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BillingCycleFinalizationService billingCycleFinalizationService;
    private final LlmRecommendationJobService llmRecommendationJobService;
    private final EmailNotificationDispatchService emailNotificationDispatchService;
    private final List<JobDefinition> jobDefinitions;
    private final Map<String, JobDefinition> jobDefinitionsByKey;

    public DatabaseScheduledJobService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            BillingCycleFinalizationService billingCycleFinalizationService,
            LlmRecommendationJobService llmRecommendationJobService,
            EmailNotificationDispatchService emailNotificationDispatchService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.billingCycleFinalizationService = billingCycleFinalizationService;
        this.llmRecommendationJobService = llmRecommendationJobService;
        this.emailNotificationDispatchService = emailNotificationDispatchService;
        this.jobDefinitions = buildJobDefinitions();
        this.jobDefinitionsByKey = jobDefinitions.stream()
                .collect(Collectors.toMap(JobDefinition::jobKey, definition -> definition));
    }

    private List<JobDefinition> buildJobDefinitions() {
        return List.of(
            new JobDefinition(
                    "rollup-appliance-usage-daily",
                    "Roll up appliance usage daily",
                    "Aggregates 30-minute appliance readings into daily appliance usage.",
                    "wattsmart.rollup_appliance_usage_daily",
                    86_400,
                    ignored -> callIntegerFunction("SELECT wattsmart.rollup_appliance_usage_daily()")),
            new JobDefinition(
                    "rollup-home-usage-daily",
                    "Roll up home usage daily",
                    "Aggregates appliance daily usage into home daily usage.",
                    "wattsmart.rollup_home_usage_daily",
                    86_400,
                    ignored -> callIntegerFunction("SELECT wattsmart.rollup_home_usage_daily()")),
            new JobDefinition(
                    "refresh-home-billing-accounts",
                    "Refresh home billing accounts",
                    "Refreshes current-cycle billing account totals from durable daily usage.",
                    "wattsmart.refresh_home_billing_accounts",
                    3_600,
                    ignored -> callIntegerFunction("SELECT wattsmart.refresh_home_billing_accounts()")),
            new JobDefinition(
                    "finalize-home-billing-cycles",
                    "Finalize home billing cycles",
                    "Snapshots ended billing cycles, rolls accounts into the next cycle, and resets live cycle totals.",
                    "wattsmart.finalize_home_billing_cycles",
                    3_600,
                    ignored -> billingCycleFinalizationService.finalizeDueBillingCycles()),
            new JobDefinition(
                    "rollup-home-usage-monthly",
                    "Roll up home usage monthly",
                    "Aggregates daily home usage into monthly prompt/reporting summaries.",
                    "wattsmart.rollup_home_usage_monthly",
                    86_400,
                    ignored -> callIntegerFunction("SELECT wattsmart.rollup_home_usage_monthly()")),
            new JobDefinition(
                    "generate-home-llm-monthly-summaries",
                    "Generate home LLM monthly summaries",
                    "Builds prompt-ready monthly home summary records.",
                    "wattsmart.generate_home_llm_monthly_summaries",
                    86_400,
                    ignored -> callIntegerFunction("SELECT wattsmart.generate_home_llm_monthly_summaries()")),
            new JobDefinition(
                    "generate-monthly-llm-recommendations",
                    "Generate monthly LLM recommendations",
                    "Generates Turkish monthly resident recommendations and queues emails.",
                    "java.llm.generate_monthly_recommendations",
                    86_400,
                    ignored -> llmRecommendationJobService.generateMonthlyRecommendations()),
            new JobDefinition(
                    "generate-urgent-llm-recommendations",
                    "Generate urgent LLM recommendations",
                    "Generates Turkish milestone/anomaly resident recommendations and queues emails.",
                    "java.llm.generate_urgent_recommendations",
                    60,
                    ignored -> llmRecommendationJobService.generateUrgentRecommendations()),
            new JobDefinition(
                    "dispatch-email-notifications",
                    "Dispatch email notifications",
                    "Sends pending resident email notifications and records delivery attempts.",
                    "java.email.dispatch_pending_notifications",
                    60,
                    ignored -> emailNotificationDispatchService.dispatchPendingEmails())
        );
    }

    @PostConstruct
    public void registerKnownJobs() {
        for (JobDefinition definition : jobDefinitions) {
            jdbcTemplate.update("""
                    INSERT INTO wattsmart.scheduled_jobs (
                        job_key,
                        name,
                        description,
                        fixed_interval_seconds,
                        handler_name,
                        next_run_at
                    )
                    VALUES (
                        :jobKey,
                        :name,
                        :description,
                        :fixedIntervalSeconds,
                        :handlerName,
                        NOW()
                    )
                    ON CONFLICT (job_key) DO UPDATE
                    SET name = EXCLUDED.name,
                        description = EXCLUDED.description,
                        fixed_interval_seconds = EXCLUDED.fixed_interval_seconds,
                        handler_name = EXCLUDED.handler_name,
                        next_run_at = COALESCE(wattsmart.scheduled_jobs.next_run_at, NOW()),
                        updated_at = NOW()
                    """, parametersFor(definition));
        }
    }

    @Scheduled(fixedDelayString = "${app.jobs.dispatch-interval-ms:60000}")
    public void runDueJobs() {
        List<ScheduledJobRow> dueJobs = jdbcTemplate.query("""
                        SELECT id::TEXT, job_key
                        FROM wattsmart.scheduled_jobs
                        WHERE status = 'ACTIVE'
                          AND job_key IN (:jobKeys)
                          AND (next_run_at IS NULL OR next_run_at <= NOW())
                        ORDER BY next_run_at NULLS FIRST, job_key
                        """,
                new MapSqlParameterSource("jobKeys", jobDefinitionsByKey.keySet()),
                (resultSet, rowNumber) -> new ScheduledJobRow(
                        resultSet.getString("id"),
                        resultSet.getString("job_key")));

        dueJobs.stream()
                .sorted(Comparator.comparingInt(row -> jobOrder(row.jobKey())))
                .forEach(dueJob -> {
                    JobDefinition definition = jobDefinitionsByKey.get(dueJob.jobKey());
                    if (definition != null) {
                        runJob(dueJob, definition);
                    }
                });
    }

    private void runJob(ScheduledJobRow dueJob, JobDefinition definition) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        Long runId = startRun(dueJob, definition, startedAt);

        try {
            Integer recordsProcessed = definition.runner().apply(definition);
            finishRun(runId, "SUCCEEDED", recordsProcessed, null, metadata(definition, startedAt, OffsetDateTime.now()));
            finishJob(dueJob, definition, "SUCCEEDED", null);
            log.info("Scheduled database job completed. jobKey={}, recordsProcessed={}", definition.jobKey(), recordsProcessed);
        } catch (RuntimeException exception) {
            finishRun(runId, "FAILED", 0, exception.getMessage(), metadata(definition, startedAt, OffsetDateTime.now()));
            finishJob(dueJob, definition, "FAILED", exception.getMessage());
            log.warn("Scheduled database job failed. jobKey={}, message={}", definition.jobKey(), exception.getMessage(), exception);
        }
    }

    private Long startRun(ScheduledJobRow dueJob, JobDefinition definition, OffsetDateTime startedAt) {
        jdbcTemplate.update("""
                        UPDATE wattsmart.scheduled_jobs
                        SET last_started_at = :startedAt,
                            last_run_status = 'STARTED',
                            last_error_message = NULL,
                            updated_at = NOW()
                        WHERE id = CAST(:jobId AS UUID)
                        """,
                new MapSqlParameterSource()
                        .addValue("jobId", dueJob.id())
                        .addValue("startedAt", startedAt));

        return jdbcTemplate.queryForObject("""
                        INSERT INTO wattsmart.scheduled_job_runs (
                            scheduled_job_id,
                            status,
                            started_at,
                            run_metadata
                        )
                        VALUES (
                            CAST(:jobId AS UUID),
                            'STARTED',
                            :startedAt,
                            CAST(:metadata AS JSONB)
                        )
                        RETURNING id
                        """,
                new MapSqlParameterSource()
                        .addValue("jobId", dueJob.id())
                        .addValue("startedAt", startedAt)
                        .addValue("metadata", metadata(definition, startedAt, null)),
                Long.class);
    }

    private void finishRun(
            Long runId,
            String status,
            int recordsProcessed,
            String errorMessage,
            String metadata
    ) {
        jdbcTemplate.update("""
                        UPDATE wattsmart.scheduled_job_runs
                        SET status = CAST(:status AS wattsmart.schedule_run_status),
                            finished_at = NOW(),
                            records_processed = :recordsProcessed,
                            error_message = :errorMessage,
                            run_metadata = CAST(:metadata AS JSONB)
                        WHERE id = :runId
                        """,
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("status", status)
                        .addValue("recordsProcessed", recordsProcessed)
                        .addValue("errorMessage", errorMessage)
                        .addValue("metadata", metadata));
    }

    private void finishJob(ScheduledJobRow dueJob, JobDefinition definition, String status, String errorMessage) {
        jdbcTemplate.update("""
                        UPDATE wattsmart.scheduled_jobs
                        SET last_completed_at = NOW(),
                            next_run_at = NOW() + (:fixedIntervalSeconds * INTERVAL '1 second'),
                            last_run_status = CAST(:status AS wattsmart.schedule_run_status),
                            last_error_message = :errorMessage,
                            updated_at = NOW()
                        WHERE id = CAST(:jobId AS UUID)
                        """,
                new MapSqlParameterSource()
                        .addValue("jobId", dueJob.id())
                        .addValue("fixedIntervalSeconds", definition.fixedIntervalSeconds())
                        .addValue("status", status)
                        .addValue("errorMessage", errorMessage));
    }

    private Integer callIntegerFunction(String sql) {
        Integer result = jdbcTemplate.getJdbcOperations().queryForObject(sql, Integer.class);
        return result != null ? result : 0;
    }

    private int jobOrder(String jobKey) {
        for (int index = 0; index < jobDefinitions.size(); index++) {
            if (jobDefinitions.get(index).jobKey().equals(jobKey)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private MapSqlParameterSource parametersFor(JobDefinition definition) {
        return new MapSqlParameterSource()
                .addValue("jobKey", definition.jobKey())
                .addValue("name", definition.name())
                .addValue("description", definition.description())
                .addValue("fixedIntervalSeconds", definition.fixedIntervalSeconds())
                .addValue("handlerName", definition.handlerName());
    }

    private String metadata(JobDefinition definition, OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("jobKey", definition.jobKey());
            metadata.put("handlerName", definition.handlerName());
            metadata.put("startedAt", startedAt);
            metadata.put("finishedAt", finishedAt);
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize scheduled job metadata.", exception);
        }
    }

    private record ScheduledJobRow(
            String id,
            String jobKey
    ) {
    }

    private record JobDefinition(
            String jobKey,
            String name,
            String description,
            String handlerName,
            int fixedIntervalSeconds,
            Function<JobDefinition, Integer> runner
    ) {
    }
}
