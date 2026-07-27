package com.wattsmart.backend.auth.api.dto;

import com.wattsmart.backend.auth.domain.UserRole;
import com.wattsmart.backend.auth.domain.UserStatus;
import java.util.List;
import java.util.UUID;

public record UserSummaryResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        List<UserRole> roles
) {
}
