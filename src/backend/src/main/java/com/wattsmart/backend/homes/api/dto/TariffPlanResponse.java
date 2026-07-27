package com.wattsmart.backend.homes.api.dto;

import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TariffPlanResponse(
        UUID tariffPlanId,
        String code,
        String name,
        String description,
        String currencyCode,
        BigDecimal baseRatePerKwh,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        List<TariffPlanMilestoneItem> milestones
) {

    public record TariffPlanMilestoneItem(
            UUID tariffPlanMilestoneId,
            UsagePercentageMilestone milestone,
            MilestoneStage stage,
            BigDecimal penaltyMultiplier
    ) {
    }
}
