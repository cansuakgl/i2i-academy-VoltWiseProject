package com.wattsmart.backend.homes.api.dto;

import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
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
            HomeStatus status,
            String timezoneName,
            BillingStatus billing,
            List<ApplianceStatusItem> appliances
    ) {
    }

    public record BillingStatus(
            LocalDate currentCycleStartedOn,
            LocalDate currentCycleEndsOn,
            BigDecimal currentCycleUsageKwh,
            BigDecimal currentCycleBaseCostAmount,
            BigDecimal currentCyclePenaltyCostAmount,
            BigDecimal totalCostAmount,
            UsagePercentageMilestone highestMilestoneReached,
            MilestoneStage highestMilestoneStage,
            BigDecimal currentTotalWatts,
            OffsetDateTime lastTelemetryReceivedAt,
            OffsetDateTime lastRollupAt
    ) {
    }

    public record ApplianceStatusItem(
            UUID applianceId,
            String applianceCode,
            String name,
            String typeCode,
            String typeDisplayName,
            BigDecimal typicalWatts,
            BigDecimal safeWattLimit,
            BigDecimal latestWattage,
            boolean aboveSafeLimit,
            int consecutiveBreachCount,
            boolean anomalyActive,
            OffsetDateTime lastCapturedAt,
            boolean active
    ) {
    }
}
