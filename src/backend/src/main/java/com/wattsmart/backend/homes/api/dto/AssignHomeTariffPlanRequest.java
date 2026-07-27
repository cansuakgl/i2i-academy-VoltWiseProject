package com.wattsmart.backend.homes.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssignHomeTariffPlanRequest(
        @NotNull UUID tariffPlanId,
        @NotNull @DecimalMin("0.001") BigDecimal monthlyUsageLimitKwh,
        Short billingCycleStartDay,
        LocalDate effectiveFrom
) {
}
