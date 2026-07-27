package com.wattsmart.backend.homes.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddHomeMemberRequest(
        @NotNull UUID userId
) {
}
