package com.wattsmart.backend.notifications;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationDispatchService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EmailSender emailSender;

    @Value("${app.email.dispatch.batch-size:25}")
    private int batchSize;

    @Transactional
    public int dispatchPendingEmails() {
        List<PendingEmailNotification> pendingNotifications = findPendingNotifications();
        int dispatched = 0;
        for (PendingEmailNotification notification : pendingNotifications) {
            if (!notification.emailEnabled()) {
                cancelDisabledNotification(notification);
                continue;
            }
            try {
                EmailSendResult result = emailSender.send(new EmailSendRequest(
                        notification.id(),
                        notification.recipientEmail(),
                        notification.subjectText(),
                        notification.bodyText()));
                recordDelivery(notification, "SENT", result.providerMessageId(), result.responsePayload(), null);
                markSent(notification);
                dispatched++;
            } catch (RuntimeException exception) {
                recordDelivery(notification, "FAILED", null, "{}", exception.getMessage());
                markFailed(notification, exception.getMessage());
                log.warn("Email notification dispatch failed. notificationId={}, message={}",
                        notification.id(),
                        exception.getMessage(),
                        exception);
            }
        }
        return dispatched;
    }

    private List<PendingEmailNotification> findPendingNotifications() {
        return jdbcTemplate.query("""
                        SELECT
                            en.id::TEXT AS id,
                            en.user_id::TEXT AS user_id,
                            en.recipient_email,
                            en.subject_text,
                            en.body_text,
                            COALESCE(unp.email_enabled, TRUE) AS email_enabled
                        FROM wattsmart.email_notifications en
                        LEFT JOIN wattsmart.user_notification_preferences unp
                            ON unp.user_id = en.user_id
                        WHERE en.status = 'PENDING'
                          AND en.scheduled_for <= NOW()
                        ORDER BY en.scheduled_for ASC, en.created_at ASC
                        LIMIT :batchSize
                        """,
                new MapSqlParameterSource("batchSize", batchSize),
                (rs, rowNum) -> new PendingEmailNotification(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("user_id") != null ? UUID.fromString(rs.getString("user_id")) : null,
                        rs.getString("recipient_email"),
                        rs.getString("subject_text"),
                        rs.getString("body_text"),
                        rs.getBoolean("email_enabled")));
    }

    private void markSent(PendingEmailNotification notification) {
        jdbcTemplate.update("""
                        UPDATE wattsmart.email_notifications
                        SET status = 'SENT',
                            sent_at = NOW(),
                            last_error = NULL,
                            updated_at = NOW()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource("id", notification.id()));
        updateRecommendationDeliveryStatus(notification, "DELIVERED", null);
    }

    private void markFailed(PendingEmailNotification notification, String errorMessage) {
        jdbcTemplate.update("""
                        UPDATE wattsmart.email_notifications
                        SET status = 'FAILED',
                            last_error = :errorMessage,
                            updated_at = NOW()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", notification.id())
                        .addValue("errorMessage", errorMessage));
        updateRecommendationDeliveryStatus(notification, "FAILED", errorMessage);
    }

    private void cancelDisabledNotification(PendingEmailNotification notification) {
        jdbcTemplate.update("""
                        UPDATE wattsmart.email_notifications
                        SET status = 'CANCELLED',
                            last_error = 'User email notifications are disabled.',
                            updated_at = NOW()
                        WHERE id = :id
                        """,
                new MapSqlParameterSource("id", notification.id()));
    }

    private void recordDelivery(
            PendingEmailNotification notification,
            String status,
            String providerMessageId,
            String responsePayload,
            String errorMessage
    ) {
        jdbcTemplate.update("""
                        INSERT INTO wattsmart.email_notification_deliveries (
                            email_notification_id,
                            status,
                            provider_message_id,
                            response_payload,
                            error_message
                        )
                        VALUES (
                            :id,
                            CAST(:status AS wattsmart.email_notification_status),
                            :providerMessageId,
                            CAST(:responsePayload AS JSONB),
                            :errorMessage
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("id", notification.id())
                        .addValue("status", status)
                        .addValue("providerMessageId", providerMessageId)
                        .addValue("responsePayload", responsePayload)
                        .addValue("errorMessage", errorMessage));
    }

    private void updateRecommendationDeliveryStatus(
            PendingEmailNotification notification,
            String status,
            String errorMessage
    ) {
        jdbcTemplate.update("""
                        UPDATE wattsmart.llm_recommendations lr
                        SET status = CAST(:status AS wattsmart.llm_recommendation_status),
                            delivered_at = CASE
                                WHEN :status = 'DELIVERED' THEN NOW()
                                ELSE delivered_at
                            END,
                            delivery_error = :errorMessage
                        FROM wattsmart.email_notifications en
                        WHERE en.id = :id
                          AND lr.id = en.llm_recommendation_id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", notification.id())
                        .addValue("status", status)
                        .addValue("errorMessage", errorMessage));
    }

    private record PendingEmailNotification(
            UUID id,
            UUID userId,
            String recipientEmail,
            String subjectText,
            String bodyText,
            boolean emailEnabled
    ) {
    }
}
