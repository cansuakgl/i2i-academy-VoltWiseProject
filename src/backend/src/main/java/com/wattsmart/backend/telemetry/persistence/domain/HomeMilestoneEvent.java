package com.wattsmart.backend.telemetry.persistence.domain;

import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "home_milestone_events", schema = "wattsmart")
public class HomeMilestoneEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "billing_account_id")
    private UUID billingAccountId;

    @Column(name = "billing_cycle_id")
    private UUID billingCycleId;

    @Column(name = "billing_cycle_started_on", nullable = false)
    private LocalDate billingCycleStartedOn;

    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private UsagePercentageMilestone milestone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private MilestoneStage stage;

    @Column(name = "usage_percentage_of_limit")
    private BigDecimal usagePercentageOfLimit;

    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt;
}
