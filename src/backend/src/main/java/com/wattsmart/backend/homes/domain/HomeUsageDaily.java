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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "home_usage_daily", schema = "wattsmart")
public class HomeUsageDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "home_id", nullable = false)
    private java.util.UUID homeId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "total_energy_kwh", nullable = false)
    private BigDecimal totalEnergyKwh;

    @Column(name = "average_watts")
    private BigDecimal averageWatts;

    @Column(name = "peak_watts")
    private BigDecimal peakWatts;

    @Column(name = "usage_percentage_of_limit")
    private BigDecimal usagePercentageOfLimit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "milestone_reached")
    private UsagePercentageMilestone milestoneReached;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "milestone_stage")
    private MilestoneStage milestoneStage;

    @Column(name = "base_cost_amount", nullable = false)
    private BigDecimal baseCostAmount;

    @Column(name = "penalty_cost_amount", nullable = false)
    private BigDecimal penaltyCostAmount;

    @Column(name = "total_cost_amount", nullable = false)
    private BigDecimal totalCostAmount;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;
}
