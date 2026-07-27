package com.wattsmart.backend.homes.api.dto;

import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyStatus;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HomeHistoryResponse(
        UUID homeId,
        LocalDate fromDate,
        LocalDate toDate,
        List<DailyUsageItem> dailyUsage,
        List<MonthlySummaryItem> monthlySummaries,
        List<BillingCycleItem> billingCycles,
        List<MilestoneEventItem> milestoneEvents,
        List<ApplianceAnomalyItem> applianceAnomalies
) {

    public record DailyUsageItem(
            LocalDate usageDate,
            BigDecimal totalEnergyKwh,
            BigDecimal averageWatts,
            BigDecimal peakWatts,
            BigDecimal usagePercentageOfLimit,
            UsagePercentageMilestone milestoneReached,
            MilestoneStage milestoneStage,
            BigDecimal baseCostAmount,
            BigDecimal penaltyCostAmount,
            BigDecimal totalCostAmount,
            int sampleCount
    ) {
    }

    public record MonthlySummaryItem(
            LocalDate monthStart,
            LocalDate monthEnd,
            BigDecimal totalEnergyKwh,
            BigDecimal averageDailyKwh,
            BigDecimal peakDailyKwh,
            BigDecimal totalBaseCostAmount,
            BigDecimal totalPenaltyCostAmount,
            BigDecimal totalCostAmount,
            UsagePercentageMilestone highestMilestoneReached,
            MilestoneStage highestMilestoneStage,
            int daysCounted
    ) {
    }

    public record BillingCycleItem(
            UUID billingCycleId,
            UUID tariffPlanId,
            LocalDate cycleStartedOn,
            LocalDate cycleEndedOn,
            short billingCycleStartDay,
            BigDecimal usageLimitKwh,
            BigDecimal totalUsageKwh,
            BigDecimal totalBaseCostAmount,
            BigDecimal totalPenaltyCostAmount,
            BigDecimal totalCostAmount,
            UsagePercentageMilestone highestMilestoneReached,
            MilestoneStage highestMilestoneStage,
            String appliedTariffCode,
            String appliedTariffName,
            String appliedCurrencyCode,
            BigDecimal appliedBaseRatePerKwh,
            OffsetDateTime finalizedAt
    ) {
    }

    public record MilestoneEventItem(
            UsagePercentageMilestone milestone,
            MilestoneStage stage,
            BigDecimal usagePercentageOfLimit,
            LocalDate usageDate,
            OffsetDateTime triggeredAt
    ) {
    }

    public record ApplianceAnomalyItem(
            UUID applianceId,
            ApplianceAnomalyType anomalyType,
            ApplianceAnomalyStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime resolvedAt,
            BigDecimal breachedSafeWattLimit,
            BigDecimal averageWatts,
            BigDecimal peakWatts,
            int consecutiveBreachCount,
            Integer durationSeconds,
            OffsetDateTime notificationSentAt,
            String notes
    ) {
    }
}
