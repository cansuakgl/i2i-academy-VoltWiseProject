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
@Table(name = "appliance_types", schema = "wattsmart")
public class ApplianceType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column
    private String description;

    @Column(name = "typical_watts")
    private BigDecimal typicalWatts;

    @Column(name = "default_safe_watt_limit")
    private BigDecimal defaultSafeWattLimit;

    @Column(name = "peak_watt_limit")
    private BigDecimal peakWattLimit;
}
