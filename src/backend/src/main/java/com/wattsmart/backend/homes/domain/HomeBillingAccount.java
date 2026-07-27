package com.wattsmart.backend.homes.domain;

import com.wattsmart.backend.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "home_billing_accounts", schema = "wattsmart")
public class HomeBillingAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_id", nullable = false)
    private Home home;

    @Column(name = "current_cycle_started_on", nullable = false)
    private LocalDate currentCycleStartedOn;

    @Column(name = "current_cycle_ends_on")
    private LocalDate currentCycleEndsOn;

    @Column(name = "current_cycle_usage_kwh", nullable = false)
    private BigDecimal currentCycleUsageKwh = BigDecimal.ZERO;

    @Column(name = "current_cycle_base_cost_amount", nullable = false)
    private BigDecimal currentCycleBaseCostAmount = BigDecimal.ZERO;

    @Column(name = "current_cycle_penalty_cost_amount", nullable = false)
    private BigDecimal currentCyclePenaltyCostAmount = BigDecimal.ZERO;

    @Column(name = "total_cost_amount", nullable = false)
    private BigDecimal totalCostAmount = BigDecimal.ZERO;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "highest_milestone_reached")
    private UsagePercentageMilestone highestMilestoneReached;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "highest_milestone_stage")
    private MilestoneStage highestMilestoneStage;

    @Column(name = "last_telemetry_received_at")
    private OffsetDateTime lastTelemetryReceivedAt;

    @Column(name = "last_rollup_at")
    private OffsetDateTime lastRollupAt;
}
