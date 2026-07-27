package com.wattsmart.backend.auth.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferencesRequest(
        @NotNull Boolean emailEnabled,
        @NotNull Boolean usageMilestoneEnabled,
        @NotNull Boolean anomalyAlertEnabled,
        @NotNull Boolean monthlySummaryEnabled
) {
}
