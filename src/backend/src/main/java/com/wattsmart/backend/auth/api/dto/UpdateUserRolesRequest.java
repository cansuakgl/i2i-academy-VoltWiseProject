package com.wattsmart.backend.auth.api.dto;

import com.wattsmart.backend.auth.domain.UserRole;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateUserRolesRequest(
        @NotEmpty Set<UserRole> roles
) {
}
