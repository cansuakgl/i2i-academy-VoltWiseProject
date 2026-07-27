package com.wattsmart.backend.homes.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HomeRegistrationRequest(
        String externalKey,
        @NotBlank String name,
        String addressLine1,
        String addressLine2,
        String city,
        String region,
        String postalCode,
        @Size(min = 2, max = 2) String countryCode,
        @NotBlank String timezoneName,
        @Valid @NotNull BillingConfigurationRequest billing,
        @Valid List<ApplianceRegistrationRequest> appliances
) {

    public record BillingConfigurationRequest(
            @NotNull UUID tariffPlanId,
            @NotNull @DecimalMin("0.001") BigDecimal monthlyUsageLimitKwh,
            Short billingCycleStartDay
    ) {
    }

    public record ApplianceRegistrationRequest(
            @NotBlank String applianceCode,
            @NotBlank String name,
            @NotBlank String typeCode,
            String manufacturer,
            String modelName,
            @DecimalMin("0.0") BigDecimal nominalWattage,
            @DecimalMin("0.0") BigDecimal safeWattLimit,
            Short displayOrder,
            OffsetDateTime installedAt
    ) {
    }
}
