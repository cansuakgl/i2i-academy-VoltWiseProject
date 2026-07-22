package com.wattsmart.backend.homes.api.dto;

import java.util.List;
import java.util.UUID;

public record HomeRegistrationResponse(
        UUID homeId,
        String externalKey,
        String name,
        UUID tariffPlanId,
        int applianceCount,
        List<UUID> applianceIds,
        UUID ownerMembershipId
) {
}
