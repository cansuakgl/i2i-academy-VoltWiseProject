package com.wattsmart.backend.homes.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegistrationOptionsResponse(
        List<TariffPlanOption> tariffPlans,
        List<ApplianceTypeOption> applianceTypes,
        List<ApplianceModelProfileOption> applianceModelProfiles
) {

    public record TariffPlanOption(
            UUID tariffPlanId,
            String code,
            String name,
            String description,
            String currencyCode,
            BigDecimal baseRatePerKwh,
            boolean active
    ) {
    }

    public record ApplianceTypeOption(
            UUID applianceTypeId,
            String code,
            String displayName,
            String description,
            BigDecimal typicalWatts,
            BigDecimal defaultSafeWattLimit,
            BigDecimal peakWattLimit
    ) {
    }

    public record ApplianceModelProfileOption(
            UUID applianceModelProfileId,
            UUID applianceTypeId,
            String typeCode,
            String typeDisplayName,
            String manufacturer,
            String modelName,
            String displayName,
            BigDecimal nominalWattage,
            BigDecimal safeWattLimit,
            BigDecimal peakWattLimit,
            String sourceName
    ) {
    }

}
