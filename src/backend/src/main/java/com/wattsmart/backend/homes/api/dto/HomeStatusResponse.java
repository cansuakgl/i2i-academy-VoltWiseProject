package com.wattsmart.backend.homes.api.dto;

import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.domain.QuotaState;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HomeStatusResponse(
        List<HomeStatusItem> homes
) {

    public record HomeStatusItem(
            UUID homeId,
            String externalKey,
            String name,
            String contactEmail,
            HomeStatus status,
            String timezoneName,
            BillingStatus billing,
            List<ApplianceStatusItem> appliances
    ) {
    }

    public record BillingStatus(
            LocalDate currentCycleStartedOn,
            BigDecimal currentCycleEnergyKwh,
            BigDecimal currentCycleBaseCostAmount,
            BigDecimal currentCyclePenaltyCostAmount,
            BigDecimal totalCostAmount,
            QuotaState quotaState,
            boolean penaltyActive,
            OffsetDateTime lastTelemetryReceivedAt,
            OffsetDateTime lastRollupAt
    ) {
    }

    public record ApplianceStatusItem(
            UUID applianceId,
            String applianceCode,
            String name,
            String typeProfileCode,
            String typeDisplayName,
            BigDecimal averageWatts,
            BigDecimal safeWattLimit,
            BigDecimal allowedDeviationPct,
            Short anomalyCycleThreshold,
            boolean active
    ) {
    }
}
