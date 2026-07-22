package com.wattsmart.backend.homes.domain;

import com.wattsmart.backend.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "home_billing_configs", schema = "wattsmart")
public class HomeBillingConfig extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_id", nullable = false)
    private Home home;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tariff_plan_id", nullable = false)
    private TariffPlan tariffPlan;

    @Column(name = "monthly_budget_amount", nullable = false)
    private BigDecimal monthlyBudgetAmount;

    @Column(name = "monthly_energy_quota_kwh")
    private BigDecimal monthlyEnergyQuotaKwh;

    @Column(name = "quota_warning_threshold_pct", nullable = false)
    private BigDecimal quotaWarningThresholdPct;

    @Column(name = "quota_critical_threshold_pct", nullable = false)
    private BigDecimal quotaCriticalThresholdPct;

    @Column(name = "billing_cycle_start_day", nullable = false)
    private short billingCycleStartDay;
}
