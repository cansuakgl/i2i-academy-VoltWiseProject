package com.wattsmart.backend.homes.events;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HomeRegistrationEvent(
        UUID homeId,
        String externalKey,
        String name,
        String contactEmail,
        String timezoneName,
        UUID tariffPlanId,
        List<RegisteredAppliance> appliances,
        OffsetDateTime registeredAt
) {

    public record RegisteredAppliance(
            UUID applianceId,
            String applianceCode,
            String applianceName,
            String typeProfileCode,
            BigDecimal averageWatts,
            BigDecimal safeWattLimit
    ) {
    }
}
