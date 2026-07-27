package com.wattsmart.backend.telemetry.persistence.domain;

import com.wattsmart.backend.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "appliance_anomalies", schema = "wattsmart")
public class ApplianceAnomaly extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "appliance_id", nullable = false)
    private UUID applianceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "anomaly_type", nullable = false)
    private ApplianceAnomalyType anomalyType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ApplianceAnomalyStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "breached_safe_watt_limit")
    private BigDecimal breachedSafeWattLimit;

    @Column(name = "average_watts")
    private BigDecimal averageWatts;

    @Column(name = "peak_watts")
    private BigDecimal peakWatts;

    @Column(name = "consecutive_breach_count", nullable = false)
    private int consecutiveBreachCount;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "notification_sent_at")
    private OffsetDateTime notificationSentAt;

    @Column
    private String notes;
}
