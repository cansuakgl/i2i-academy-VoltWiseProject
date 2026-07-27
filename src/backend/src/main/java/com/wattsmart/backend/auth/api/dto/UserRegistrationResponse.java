package com.wattsmart.backend.auth.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserRegistrationResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        OffsetDateTime registeredAt
) {
}
