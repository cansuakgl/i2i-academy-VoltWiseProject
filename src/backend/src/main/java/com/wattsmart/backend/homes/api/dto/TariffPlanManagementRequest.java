package com.wattsmart.backend.homes.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TariffPlanManagementRequest(
        @NotBlank String code,
        @NotBlank String name,
        String description,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        @NotNull @DecimalMin("0.0") BigDecimal baseRatePerKwh,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active,
        @Valid List<TariffPlanMilestoneRequest> milestones
) {
}
