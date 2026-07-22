package com.wattsmart.backend.homes.domain;

import com.wattsmart.backend.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "appliance_type_profiles", schema = "wattsmart")
public class ApplianceTypeProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "average_watts", nullable = false)
    private BigDecimal averageWatts;

    @Column(name = "default_safe_watt_limit", nullable = false)
    private BigDecimal defaultSafeWattLimit;

    @Column(name = "allowed_deviation_pct", nullable = false)
    private BigDecimal allowedDeviationPct;

    @Column(name = "default_anomaly_cycle_threshold", nullable = false)
    private short defaultAnomalyCycleThreshold;
}
