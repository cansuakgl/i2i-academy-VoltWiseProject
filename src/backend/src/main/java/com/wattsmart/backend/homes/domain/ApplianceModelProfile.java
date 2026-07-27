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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "appliance_model_profiles", schema = "wattsmart")
public class ApplianceModelProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appliance_type_id", nullable = false)
    private ApplianceType applianceType;

    @Column(nullable = false)
    private String manufacturer;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "nominal_wattage")
    private BigDecimal nominalWattage;

    @Column(name = "safe_watt_limit")
    private BigDecimal safeWattLimit;

    @Column(name = "peak_watt_limit")
    private BigDecimal peakWattLimit;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column
    private String notes;
}
