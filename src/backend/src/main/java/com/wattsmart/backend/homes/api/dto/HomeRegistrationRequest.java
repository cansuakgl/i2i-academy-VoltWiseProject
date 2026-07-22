package com.wattsmart.backend.homes.api.dto;

import com.wattsmart.backend.homes.domain.MembershipRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HomeRegistrationRequest(
        @NotBlank String externalKey,
        @NotBlank String name,
        @NotBlank @Email String contactEmail,
        String addressLine1,
        String addressLine2,
        String city,
        String region,
        String postalCode,
        @Size(min = 2, max = 2) String countryCode,
        @NotBlank String timezoneName,
        @Valid @NotNull BillingConfigurationRequest billing,
        @Valid @NotEmpty List<ApplianceRegistrationRequest> appliances,
        UUID ownerUserId,
        MembershipRole ownerMembershipRole,
        Boolean primaryOwner
) {

    public record BillingConfigurationRequest(
            UUID tariffPlanId,
            @NotNull @DecimalMin("0.0") BigDecimal monthlyBudgetAmount,
            @DecimalMin("0.0") BigDecimal monthlyEnergyQuotaKwh,
            @DecimalMin("0.01") BigDecimal quotaWarningThresholdPct,
            @DecimalMin("100.0") BigDecimal quotaCriticalThresholdPct,
            Short billingCycleStartDay
    ) {
    }

    public record ApplianceRegistrationRequest(
            @NotBlank String applianceCode,
            @NotBlank String name,
            @NotBlank String typeProfileCode,
            String manufacturer,
            String modelNumber,
            @DecimalMin("0.0") BigDecimal nominalWattage,
            @DecimalMin("0.0") BigDecimal safeWattLimit,
            @DecimalMin("0.0") BigDecimal allowedDeviationPct,
            Short anomalyCycleThreshold,
            Short displayOrder,
            OffsetDateTime installedAt
    ) {
    }
}
