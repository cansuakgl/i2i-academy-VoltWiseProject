package com.wattsmart.backend.auth.api.dto;

import java.util.UUID;

public record NotificationPreferencesResponse(
        UUID userId,
        boolean emailEnabled,
        boolean usageMilestoneEnabled,
        boolean anomalyAlertEnabled,
        boolean monthlySummaryEnabled
) {
}
