package com.wattsmart.backend.homes.domain;

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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "home_usage_monthly_summaries", schema = "wattsmart")
public class HomeUsageMonthlySummary extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "home_id", nullable = false)
    private UUID homeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "period_type", nullable = false)
    private SummaryPeriodType periodType;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "month_end", nullable = false)
    private LocalDate monthEnd;

    @Column(name = "total_energy_kwh", nullable = false)
    private BigDecimal totalEnergyKwh;

    @Column(name = "average_daily_kwh")
    private BigDecimal averageDailyKwh;

    @Column(name = "peak_daily_kwh")
    private BigDecimal peakDailyKwh;

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

    @Column(name = "days_counted", nullable = false)
    private int daysCounted;
}
