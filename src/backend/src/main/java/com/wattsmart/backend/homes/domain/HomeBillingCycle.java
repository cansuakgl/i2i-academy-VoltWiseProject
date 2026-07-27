package com.wattsmart.backend.homes.domain;

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
@Table(name = "home_billing_cycles", schema = "wattsmart")
public class HomeBillingCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Column(name = "tariff_plan_id")
    private UUID tariffPlanId;

    @Column(name = "cycle_started_on", nullable = false)
    private LocalDate cycleStartedOn;

    @Column(name = "cycle_ended_on", nullable = false)
    private LocalDate cycleEndedOn;

    @Column(name = "billing_cycle_start_day", nullable = false)
    private short billingCycleStartDay;

    @Column(name = "usage_limit_kwh", nullable = false)
    private BigDecimal usageLimitKwh;

    @Column(name = "total_usage_kwh", nullable = false)
    private BigDecimal totalUsageKwh;

    @Column(name = "total_base_cost_amount", nullable = false)
    private BigDecimal totalBaseCostAmount;

    @Column(name = "total_penalty_cost_amount", nullable = false)
    private BigDecimal totalPenaltyCostAmount;

    @Column(name = "total_cost_amount", nullable = false)
    private BigDecimal totalCostAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "highest_milestone_reached")
    private UsagePercentageMilestone highestMilestoneReached;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "highest_milestone_stage")
    private MilestoneStage highestMilestoneStage;

    @Column(name = "applied_tariff_code")
    private String appliedTariffCode;

    @Column(name = "applied_tariff_name")
    private String appliedTariffName;

    @Column(name = "applied_currency_code")
    private String appliedCurrencyCode;

    @Column(name = "applied_base_rate_per_kwh")
    private BigDecimal appliedBaseRatePerKwh;

    @Column(name = "finalized_at", nullable = false)
    private OffsetDateTime finalizedAt;
}
