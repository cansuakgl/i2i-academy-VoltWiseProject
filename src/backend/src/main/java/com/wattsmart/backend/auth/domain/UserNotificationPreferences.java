package com.wattsmart.backend.auth.domain;

import com.wattsmart.backend.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_notification_preferences", schema = "wattsmart")
public class UserNotificationPreferences extends AuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "usage_milestone_enabled", nullable = false)
    private boolean usageMilestoneEnabled = true;

    @Column(name = "anomaly_alert_enabled", nullable = false)
    private boolean anomalyAlertEnabled = true;

    @Column(name = "monthly_summary_enabled", nullable = false)
    private boolean monthlySummaryEnabled = true;

}
