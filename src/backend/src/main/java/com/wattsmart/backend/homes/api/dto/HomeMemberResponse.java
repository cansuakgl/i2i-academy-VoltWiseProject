package com.wattsmart.backend.homes.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HomeMemberResponse(
        UUID membershipId,
        UUID homeId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        OffsetDateTime invitedAt,
        OffsetDateTime acceptedAt
) {
}
