package com.wattsmart.backend.homes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ApplianceManagementRequest(
        @NotBlank String applianceCode,
        @NotBlank String name,
        @NotBlank String typeCode,
        String manufacturer,
        String modelName,
        @DecimalMin("0.0") BigDecimal nominalWattage,
        @DecimalMin("0.001") BigDecimal safeWattLimit,
        Short displayOrder,
        OffsetDateTime installedAt
) {
}
