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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "appliances", schema = "wattsmart")
public class Appliance extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_id", nullable = false)
    private Home home;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appliance_type_profile_id", nullable = false)
    private ApplianceTypeProfile applianceTypeProfile;

    @Column(name = "appliance_code", nullable = false)
    private String applianceCode;

    @Column(nullable = false)
    private String name;

    @Column
    private String manufacturer;

    @Column(name = "model_number")
    private String modelNumber;

    @Column(name = "nominal_wattage")
    private BigDecimal nominalWattage;

    @Column(name = "safe_watt_limit")
    private BigDecimal safeWattLimit;

    @Column(name = "allowed_deviation_pct")
    private BigDecimal allowedDeviationPct;

    @Column(name = "anomaly_cycle_threshold")
    private Short anomalyCycleThreshold;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "installed_at")
    private OffsetDateTime installedAt;
}
