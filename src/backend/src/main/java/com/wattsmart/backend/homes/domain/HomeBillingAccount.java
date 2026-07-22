package com.wattsmart.backend.homes.domain;

import com.wattsmart.backend.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "current_cycle_energy_kwh", nullable = false)
    private BigDecimal currentCycleEnergyKwh = BigDecimal.ZERO;

    @Column(name = "current_cycle_base_cost_amount", nullable = false)
    private BigDecimal currentCycleBaseCostAmount = BigDecimal.ZERO;

    @Column(name = "current_cycle_penalty_cost_amount", nullable = false)
    private BigDecimal currentCyclePenaltyCostAmount = BigDecimal.ZERO;

    @Column(name = "total_cost_amount", nullable = false)
    private BigDecimal totalCostAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_state", nullable = false)
    private QuotaState quotaState = QuotaState.NORMAL;

    @Column(name = "penalty_active", nullable = false)
    private boolean penaltyActive;
}
