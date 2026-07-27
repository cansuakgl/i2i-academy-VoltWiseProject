package com.wattsmart.backend.telemetry.service;

import com.wattsmart.backend.homes.domain.HomeTariffPlan;
import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.TariffPlanMilestone;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import com.wattsmart.backend.homes.repository.HomeTariffPlanRepository;
import com.wattsmart.backend.homes.repository.TariffPlanMilestoneRepository;
import com.wattsmart.backend.telemetry.events.ApplianceTelemetryEvent;
import com.wattsmart.backend.telemetry.live.LiveApplianceState;
import com.wattsmart.backend.telemetry.live.LiveApplianceUsageWindow;
import com.wattsmart.backend.telemetry.live.LiveHomeState;
import com.wattsmart.backend.telemetry.live.LiveHomeStateStore;
import com.wattsmart.backend.telemetry.live.LiveStateTimeCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryProcessingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final Duration ANOMALY_MINIMUM_BREACH_DURATION = Duration.ofSeconds(60);

    private final HomeTariffPlanRepository homeTariffPlanRepository;
    private final TariffPlanMilestoneRepository tariffPlanMilestoneRepository;
    private final LiveHomeStateStore liveHomeStateStore;
    private final HomeTelemetryLockService homeTelemetryLockService;

    public void process(ApplianceTelemetryEvent event) {
        homeTelemetryLockService.withHomeLock(event.homeId(), () -> processWithHomeLock(event));
    }

    private void processWithHomeLock(ApplianceTelemetryEvent event) {
        HomeTariffPlan homeTariffPlan = homeTariffPlanRepository.findByHomeId(event.homeId())
                .orElseThrow(() -> new IllegalStateException("No home tariff plan configured for home " + event.homeId()));

        LiveHomeState currentState = liveHomeStateStore.getHomeState(event.homeId());
        BigDecimal totalInstantWatts = event.readings().stream()
                .map(ApplianceTelemetryEvent.ApplianceReading::wattage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long secondsSincePreviousCapture = secondsBetween(
                currentState != null ? LiveStateTimeCodec.toOffsetDateTime(currentState.lastCapturedAt()) : null,
                event.capturedAt());

        BigDecimal usageIncrementKwh = calculateUsageIncrementKwh(totalInstantWatts, secondsSincePreviousCapture);
        BigDecimal currentCycleUsageKwh = add(
                currentState != null ? currentState.currentCycleUsageKwh() : ZERO,
                usageIncrementKwh);
        BigDecimal previousCycleUsageKwh = currentState != null ? currentState.currentCycleUsageKwh() : ZERO;

        BigDecimal usagePercentage = calculateUsagePercentage(
                currentCycleUsageKwh,
                homeTariffPlan.getMonthlyUsageLimitKwh());

        List<TariffPlanMilestone> tariffMilestones = tariffPlanMilestoneRepository
                .findByTariffPlanId(homeTariffPlan.getTariffPlan().getId());
        TariffPlanMilestone reachedMilestone = resolveReachedMilestone(tariffMilestones, usagePercentage);

        BigDecimal baseCostIncrement = calculateBaseCostIncrement(
                usageIncrementKwh,
                homeTariffPlan.getTariffPlan().getBaseRatePerKwh());

        BigDecimal penaltyCostIncrement = calculatePenaltyCostIncrement(
                usageIncrementKwh,
                previousCycleUsageKwh,
                homeTariffPlan.getTariffPlan().getBaseRatePerKwh(),
                homeTariffPlan.getMonthlyUsageLimitKwh(),
                tariffMilestones);

        BigDecimal currentCycleBaseCostAmount = add(
                currentState != null ? currentState.currentCycleBaseCostAmount() : ZERO,
                baseCostIncrement);
        BigDecimal currentCyclePenaltyCostAmount = add(
                currentState != null ? currentState.currentCyclePenaltyCostAmount() : ZERO,
                penaltyCostIncrement);
        BigDecimal totalCostAmount = add(currentCycleBaseCostAmount, currentCyclePenaltyCostAmount);

        Map<UUID, LiveApplianceState> applianceStates = buildUpdatedApplianceStates(currentState, event);

        LiveHomeState updatedState = new LiveHomeState(
                event.homeId(),
                event.homeExternalKey(),
                currentState != null ? currentState.homeName() : event.homeExternalKey(),
                currentState != null ? currentState.homeStatus() : HomeStatus.ACTIVE,
                currentState != null ? currentState.timezoneName() : "Europe/Istanbul",
                currentState != null ? currentState.currentCycleStartedOn() : null,
                currentState != null ? currentState.currentCycleEndsOn() : null,
                LiveStateTimeCodec.toIso(event.capturedAt()),
                totalInstantWatts.setScale(2, RoundingMode.HALF_UP),
                currentCycleUsageKwh,
                currentCycleBaseCostAmount,
                currentCyclePenaltyCostAmount,
                totalCostAmount,
                reachedMilestone != null ? reachedMilestone.getMilestone() : null,
                reachedMilestone != null ? reachedMilestone.getStage() : null,
                new HashMap<>(applianceStates));

        liveHomeStateStore.saveHomeState(updatedState);

        log.info(
                "Processed telemetry event. homeId={}, readings={}, totalWattage={}, usageIncrementKwh={}, milestone={}",
                event.homeId(),
                event.readings().size(),
                totalInstantWatts,
                usageIncrementKwh,
                updatedState.highestMilestoneReached());
    }

    private Map<UUID, LiveApplianceState> buildUpdatedApplianceStates(
            LiveHomeState currentState,
            ApplianceTelemetryEvent event
    ) {
        Map<UUID, LiveApplianceState> updatedStates = new HashMap<>();
        if (currentState != null) {
            updatedStates.putAll(currentState.appliances());
        }

        for (ApplianceTelemetryEvent.ApplianceReading reading : event.readings()) {
            LiveApplianceState previousState = updatedStates.get(reading.applianceId());
            int consecutiveBreachCount = reading.aboveSafeLimit()
                    ? (previousState != null ? previousState.consecutiveBreachCount() : 0) + 1
                    : 0;
            OffsetDateTime breachStartedAt = resolveBreachStartedAt(previousState, reading, event.capturedAt());
            UsageWindowUpdate windowUpdate = updateUsageWindow(previousState, reading, event.capturedAt());

            updatedStates.put(
                    reading.applianceId(),
                    new LiveApplianceState(
                            reading.applianceId(),
                            reading.applianceCode(),
                            previousState != null ? previousState.applianceName() : reading.applianceCode(),
                            reading.applianceTypeCode(),
                            previousState != null ? previousState.applianceTypeDisplayName() : reading.applianceTypeCode(),
                            previousState != null ? previousState.typicalWatts() : null,
                            reading.wattage().setScale(2, RoundingMode.HALF_UP),
                            reading.safeWattLimit(),
                            reading.aboveSafeLimit(),
                            consecutiveBreachCount,
                            LiveStateTimeCodec.toIso(breachStartedAt),
                            isAnomalyActive(reading.aboveSafeLimit(), breachStartedAt, event.capturedAt()),
                            LiveStateTimeCodec.toIso(event.capturedAt()),
                            windowUpdate.currentWindow(),
                            windowUpdate.completedWindow(),
                            previousState == null || previousState.active()));
        }

        return updatedStates;
    }

    private OffsetDateTime resolveBreachStartedAt(
            LiveApplianceState previousState,
            ApplianceTelemetryEvent.ApplianceReading reading,
            OffsetDateTime capturedAt
    ) {
        if (!reading.aboveSafeLimit()) {
            return null;
        }
        if (previousState != null && previousState.aboveSafeLimit() && previousState.breachStartedAt() != null) {
            return LiveStateTimeCodec.toOffsetDateTime(previousState.breachStartedAt());
        }
        return capturedAt;
    }

    private boolean isAnomalyActive(boolean aboveSafeLimit, OffsetDateTime breachStartedAt, OffsetDateTime capturedAt) {
        return aboveSafeLimit
                && breachStartedAt != null
                && !Duration.between(breachStartedAt, capturedAt).minus(ANOMALY_MINIMUM_BREACH_DURATION).isNegative();
    }

    private UsageWindowUpdate updateUsageWindow(
            LiveApplianceState previousState,
            ApplianceTelemetryEvent.ApplianceReading reading,
            OffsetDateTime capturedAt
    ) {
        OffsetDateTime currentWindowStart = windowStart(capturedAt);
        OffsetDateTime currentWindowEnd = currentWindowStart.plusMinutes(30);
        LiveApplianceUsageWindow currentWindow = previousState != null ? previousState.currentUsageWindow() : null;
        LiveApplianceUsageWindow completedWindow = previousState != null ? previousState.completedUsageWindow() : null;
        OffsetDateTime previousCapturedAt = previousState != null
                ? LiveStateTimeCodec.toOffsetDateTime(previousState.lastCapturedAt())
                : null;
        long secondsForCurrentWindow;

        if (currentWindow == null || !LiveStateTimeCodec.toOffsetDateTime(currentWindow.windowStartedAt()).equals(currentWindowStart)) {
            if (currentWindow != null && previousCapturedAt != null && capturedAt.isAfter(LiveStateTimeCodec.toOffsetDateTime(currentWindow.windowEndedAt()))) {
                currentWindow = addToWindow(
                        currentWindow,
                        reading.wattage(),
                        secondsBetween(previousCapturedAt, LiveStateTimeCodec.toOffsetDateTime(currentWindow.windowEndedAt())));
            }
            completedWindow = currentWindow;
            currentWindow = new LiveApplianceUsageWindow(
                    LiveStateTimeCodec.toIso(currentWindowStart),
                    LiveStateTimeCodec.toIso(currentWindowEnd),
                    ZERO,
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                    reading.wattage().setScale(2, RoundingMode.HALF_UP),
                    0);
            secondsForCurrentWindow = secondsBetween(
                    previousCapturedAt != null && previousCapturedAt.isAfter(currentWindowStart) ? previousCapturedAt : currentWindowStart,
                    capturedAt);
        } else {
            secondsForCurrentWindow = secondsBetween(previousCapturedAt, capturedAt);
        }

        currentWindow = addToWindow(currentWindow, reading.wattage(), secondsForCurrentWindow);

        return new UsageWindowUpdate(currentWindow, completedWindow);
    }

    private LiveApplianceUsageWindow addToWindow(
            LiveApplianceUsageWindow usageWindow,
            BigDecimal wattage,
            long seconds
    ) {
        BigDecimal energyIncrementKwh = calculateUsageIncrementKwh(wattage, seconds);
        BigDecimal wattSecondsIncrement = wattage
                .multiply(BigDecimal.valueOf(Math.max(seconds, 0)))
                .setScale(6, RoundingMode.HALF_UP);

        return new LiveApplianceUsageWindow(
                usageWindow.windowStartedAt(),
                usageWindow.windowEndedAt(),
                usageWindow.energyKwh().add(energyIncrementKwh).setScale(6, RoundingMode.HALF_UP),
                usageWindow.wattSeconds().add(wattSecondsIncrement).setScale(6, RoundingMode.HALF_UP),
                usageWindow.peakWatts().max(wattage).setScale(2, RoundingMode.HALF_UP),
                usageWindow.sampleCount() + 1);
    }

    private OffsetDateTime windowStart(OffsetDateTime capturedAt) {
        OffsetDateTime truncated = capturedAt.truncatedTo(ChronoUnit.MINUTES);
        int minuteBucket = (truncated.getMinute() / 30) * 30;
        return truncated.withMinute(minuteBucket).withSecond(0).withNano(0);
    }

    private TariffPlanMilestone resolveReachedMilestone(List<TariffPlanMilestone> milestones, BigDecimal usagePercentage) {
        if (usagePercentage == null) {
            return null;
        }

        return milestones.stream()
                .filter(milestone -> usagePercentage.compareTo(BigDecimal.valueOf(thresholdFor(milestone.getMilestone()))) >= 0)
                .max(Comparator.comparingInt(milestone -> thresholdFor(milestone.getMilestone())))
                .orElse(null);
    }

    private int thresholdFor(UsagePercentageMilestone milestone) {
        return switch (milestone) {
            case PCT_80 -> 80;
            case PCT_100 -> 100;
            case PCT_120 -> 120;
            case PCT_130 -> 130;
            case PCT_150 -> 150;
            case PCT_180 -> 180;
        };
    }

    private BigDecimal calculateUsageIncrementKwh(BigDecimal totalInstantWatts, long secondsSincePreviousCapture) {
        if (secondsSincePreviousCapture <= 0) {
            return ZERO;
        }

        return totalInstantWatts
                .multiply(BigDecimal.valueOf(secondsSincePreviousCapture))
                .divide(BigDecimal.valueOf(3_600_000), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateUsagePercentage(BigDecimal currentCycleUsageKwh, BigDecimal monthlyUsageLimitKwh) {
        if (monthlyUsageLimitKwh == null || monthlyUsageLimitKwh.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return currentCycleUsageKwh
                .multiply(BigDecimal.valueOf(100))
                .divide(monthlyUsageLimitKwh, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBaseCostIncrement(BigDecimal usageIncrementKwh, BigDecimal baseRatePerKwh) {
        return usageIncrementKwh.multiply(baseRatePerKwh).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePenaltyCostIncrement(
            BigDecimal usageIncrementKwh,
            BigDecimal previousCycleUsageKwh,
            BigDecimal baseRatePerKwh,
            BigDecimal monthlyUsageLimitKwh,
            List<TariffPlanMilestone> milestones
    ) {
        if (usageIncrementKwh.compareTo(BigDecimal.ZERO) <= 0
                || monthlyUsageLimitKwh == null
                || monthlyUsageLimitKwh.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal currentCycleUsageKwh = previousCycleUsageKwh.add(usageIncrementKwh);
        BigDecimal penaltyCost = BigDecimal.ZERO;
        BigDecimal segmentStartKwh = previousCycleUsageKwh;

        List<TariffPlanMilestone> penaltyMilestones = milestones.stream()
                .filter(milestone -> milestone.getStage() == MilestoneStage.PENALTY)
                .filter(milestone -> milestone.getPenaltyMultiplier() != null)
                .sorted(Comparator.comparingInt(milestone -> thresholdFor(milestone.getMilestone())))
                .toList();

        for (TariffPlanMilestone milestone : penaltyMilestones) {
            BigDecimal thresholdKwh = monthlyUsageLimitKwh
                    .multiply(BigDecimal.valueOf(thresholdFor(milestone.getMilestone())))
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            if (currentCycleUsageKwh.compareTo(thresholdKwh) <= 0) {
                break;
            }

            BigDecimal nextThresholdKwh = nextPenaltyThresholdKwh(milestone, penaltyMilestones, monthlyUsageLimitKwh);
            BigDecimal segmentEndKwh = currentCycleUsageKwh.min(nextThresholdKwh);
            BigDecimal chargeableSegmentKwh = segmentEndKwh.subtract(segmentStartKwh.max(thresholdKwh));
            if (chargeableSegmentKwh.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal penaltyMultiplierDelta = milestone.getPenaltyMultiplier().subtract(BigDecimal.ONE);
                if (penaltyMultiplierDelta.compareTo(BigDecimal.ZERO) > 0) {
                    penaltyCost = penaltyCost.add(
                            chargeableSegmentKwh
                                    .multiply(baseRatePerKwh)
                                    .multiply(penaltyMultiplierDelta));
                }
            }
        }

        return penaltyCost.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nextPenaltyThresholdKwh(
            TariffPlanMilestone currentMilestone,
            List<TariffPlanMilestone> penaltyMilestones,
            BigDecimal monthlyUsageLimitKwh
    ) {
        return penaltyMilestones.stream()
                .filter(milestone -> thresholdFor(milestone.getMilestone()) > thresholdFor(currentMilestone.getMilestone()))
                .map(milestone -> monthlyUsageLimitKwh
                        .multiply(BigDecimal.valueOf(thresholdFor(milestone.getMilestone())))
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                .findFirst()
                .orElse(new BigDecimal("999999999999.000000"));
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        return left.add(right).setScale(6, RoundingMode.HALF_UP);
    }

    private long secondsBetween(OffsetDateTime previous, OffsetDateTime current) {
        if (previous == null || current == null || current.isBefore(previous)) {
            return 0;
        }
        return Duration.between(previous, current).getSeconds();
    }

    private record UsageWindowUpdate(
            LiveApplianceUsageWindow currentWindow,
            LiveApplianceUsageWindow completedWindow
    ) {
    }
}
