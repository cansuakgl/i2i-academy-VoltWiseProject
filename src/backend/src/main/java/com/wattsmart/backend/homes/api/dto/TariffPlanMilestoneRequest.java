package com.wattsmart.backend.homes.api.dto;

import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TariffPlanMilestoneRequest(
        @NotNull UsagePercentageMilestone milestone,
        @NotNull MilestoneStage stage,
        @DecimalMin("1.0") BigDecimal penaltyMultiplier
) {
}
