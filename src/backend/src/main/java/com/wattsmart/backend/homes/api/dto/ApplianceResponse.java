package com.wattsmart.backend.homes.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplianceResponse(
        UUID applianceId,
        UUID homeId,
        UUID applianceTypeId,
        String applianceTypeCode,
        String applianceTypeDisplayName,
        String applianceCode,
        String name,
        String manufacturer,
        String modelName,
        BigDecimal nominalWattage,
        BigDecimal safeWattLimit,
        short displayOrder,
        boolean active,
        OffsetDateTime installedAt
) {
}
