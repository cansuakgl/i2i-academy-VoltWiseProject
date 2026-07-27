package com.wattsmart.backend.homes.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record HomeTariffPlanResponse(
        UUID homeTariffPlanId,
        UUID homeId,
        UUID tariffPlanId,
        String tariffPlanCode,
        String tariffPlanName,
        BigDecimal monthlyUsageLimitKwh,
        short billingCycleStartDay,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
