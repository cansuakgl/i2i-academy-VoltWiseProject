package com.wattsmart.backend.auth.api.dto;

import com.wattsmart.backend.auth.domain.UserRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AuthSessionResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        List<UserRole> roles,
        String accessToken,
        String tokenType,
        OffsetDateTime expiresAt,
        List<UUID> homeIds
) {
}
