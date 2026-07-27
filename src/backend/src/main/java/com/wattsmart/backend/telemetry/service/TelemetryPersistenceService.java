package com.wattsmart.backend.telemetry.service;

import com.wattsmart.backend.homes.domain.HomeBillingAccount;
import com.wattsmart.backend.homes.domain.HomeTariffPlan;
import com.wattsmart.backend.homes.repository.HomeBillingAccountRepository;
import com.wattsmart.backend.homes.repository.HomeTariffPlanRepository;
import com.wattsmart.backend.telemetry.live.LiveApplianceState;
import com.wattsmart.backend.telemetry.live.LiveApplianceUsageWindow;
import com.wattsmart.backend.telemetry.live.LiveHomeState;
import com.wattsmart.backend.telemetry.live.LiveHomeStateStore;
import com.wattsmart.backend.telemetry.live.LiveStateTimeCodec;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomaly;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyStatus;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyType;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceUsageReading;
import com.wattsmart.backend.telemetry.persistence.domain.HomeMilestoneEvent;
import com.wattsmart.backend.telemetry.persistence.repository.ApplianceAnomalyRepository;
import com.wattsmart.backend.telemetry.persistence.repository.ApplianceUsageReadingRepository;
import com.wattsmart.backend.telemetry.persistence.repository.HomeMilestoneEventRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryPersistenceService {

    private final LiveHomeStateStore liveHomeStateStore;
    private final HomeBillingAccountRepository homeBillingAccountRepository;
    private final HomeTariffPlanRepository homeTariffPlanRepository;
    private final ApplianceUsageReadingRepository applianceUsageReadingRepository;
    private final ApplianceAnomalyRepository applianceAnomalyRepository;
    private final HomeMilestoneEventRepository homeMilestoneEventRepository;

    @Scheduled(fixedDelayString = "${app.persistence.readings-interval-ms:1800000}")
    @Transactional
    public void persistThirtyMinuteReadings() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (Map.Entry<UUID, LiveHomeState> entry : liveHomeStateStore.getAllHomeStates().entrySet()) {
            UUID homeId = entry.getKey();
            LiveHomeState liveHomeState = entry.getValue();
            Map<UUID, LiveApplianceState> updatedApplianceStates = new HashMap<>(liveHomeState.appliances());
            boolean flushedAnyWindow = false;

            HomeTariffPlan homeTariffPlan = homeTariffPlanRepository.findByHomeId(homeId).orElse(null);
            if (homeTariffPlan == null) {
                continue;
            }

            for (LiveApplianceState applianceState : liveHomeState.appliances().values()) {
                LiveApplianceUsageWindow windowToPersist = windowReadyToPersist(applianceState, now);
                if (windowToPersist == null || windowToPersist.sampleCount() <= 0) {
                    continue;
                }

                persistUsageWindow(homeId, applianceState.applianceId(), windowToPersist);
                updatedApplianceStates.put(applianceState.applianceId(), clearPersistedWindow(applianceState, windowToPersist));
                flushedAnyWindow = true;
            }

            if (flushedAnyWindow) {
                liveHomeStateStore.saveHomeState(new LiveHomeState(
                        liveHomeState.homeId(),
                        liveHomeState.homeExternalKey(),
                        liveHomeState.homeName(),
                        liveHomeState.homeStatus(),
                        liveHomeState.timezoneName(),
                        liveHomeState.currentCycleStartedOn(),
                        liveHomeState.currentCycleEndsOn(),
                        liveHomeState.lastCapturedAt(),
                        liveHomeState.totalInstantWatts(),
                        liveHomeState.currentCycleUsageKwh(),
                        liveHomeState.currentCycleBaseCostAmount(),
                        liveHomeState.currentCyclePenaltyCostAmount(),
                        liveHomeState.totalCostAmount(),
                        liveHomeState.highestMilestoneReached(),
                        liveHomeState.highestMilestoneStage(),
                        new HashMap<>(updatedApplianceStates)));
            }
        }
    }

    private LiveApplianceUsageWindow windowReadyToPersist(LiveApplianceState applianceState, OffsetDateTime now) {
        if (applianceState.completedUsageWindow() != null) {
            return applianceState.completedUsageWindow();
        }
        if (applianceState.currentUsageWindow() != null
                && !LiveStateTimeCodec.toOffsetDateTime(applianceState.currentUsageWindow().windowEndedAt()).isAfter(now)) {
            return applianceState.currentUsageWindow();
        }
        return null;
    }

    private void persistUsageWindow(UUID homeId, UUID applianceId, LiveApplianceUsageWindow usageWindow) {
        ApplianceUsageReading reading = applianceUsageReadingRepository
                .findByApplianceIdAndReadingWindowStartedAtAndReadingWindowEndedAt(
                        applianceId,
                        LiveStateTimeCodec.toOffsetDateTime(usageWindow.windowStartedAt()),
                        LiveStateTimeCodec.toOffsetDateTime(usageWindow.windowEndedAt()))
                .orElseGet(ApplianceUsageReading::new);

        reading.setHomeId(homeId);
        reading.setApplianceId(applianceId);
        reading.setReadingWindowStartedAt(LiveStateTimeCodec.toOffsetDateTime(usageWindow.windowStartedAt()));
        reading.setReadingWindowEndedAt(LiveStateTimeCodec.toOffsetDateTime(usageWindow.windowEndedAt()));
        reading.setAverageWatts(calculateAverageWatts(usageWindow));
        reading.setPeakWatts(usageWindow.peakWatts());
        reading.setEnergyKwh(usageWindow.energyKwh());
        reading.setSampleCount(usageWindow.sampleCount());
        applianceUsageReadingRepository.save(reading);
    }

    private LiveApplianceState clearPersistedWindow(
            LiveApplianceState applianceState,
            LiveApplianceUsageWindow persistedWindow
    ) {
        LiveApplianceUsageWindow currentWindow = applianceState.currentUsageWindow();
        LiveApplianceUsageWindow completedWindow = applianceState.completedUsageWindow();

        if (completedWindow != null && completedWindow.windowStartedAt().equals(persistedWindow.windowStartedAt())) {
            completedWindow = null;
        }
        if (currentWindow != null && currentWindow.windowStartedAt().equals(persistedWindow.windowStartedAt())) {
            currentWindow = null;
        }

        return new LiveApplianceState(
                applianceState.applianceId(),
                applianceState.applianceCode(),
                applianceState.applianceName(),
                applianceState.applianceTypeCode(),
                applianceState.applianceTypeDisplayName(),
                applianceState.typicalWatts(),
                applianceState.latestWattage(),
                applianceState.safeWattLimit(),
                applianceState.aboveSafeLimit(),
                applianceState.consecutiveBreachCount(),
                applianceState.breachStartedAt(),
                applianceState.anomalyActive(),
                applianceState.lastCapturedAt(),
                currentWindow,
                completedWindow,
                applianceState.active());
    }

    private BigDecimal calculateAverageWatts(LiveApplianceUsageWindow usageWindow) {
        long durationSeconds = Duration.between(
                LiveStateTimeCodec.toOffsetDateTime(usageWindow.windowStartedAt()),
                LiveStateTimeCodec.toOffsetDateTime(usageWindow.windowEndedAt())).getSeconds();
        if (durationSeconds <= 0) {
            return usageWindow.peakWatts();
        }
        BigDecimal averageWatts = usageWindow.wattSeconds()
                .divide(BigDecimal.valueOf(durationSeconds), 2, RoundingMode.HALF_UP);
        return averageWatts.compareTo(BigDecimal.ZERO) > 0 ? averageWatts : usageWindow.peakWatts();
    }

    @Scheduled(fixedDelayString = "${app.persistence.events-interval-ms:60000}")
    @Transactional
    public void persistLiveEvents() {
        for (Map.Entry<UUID, LiveHomeState> entry : liveHomeStateStore.getAllHomeStates().entrySet()) {
            UUID homeId = entry.getKey();
            LiveHomeState liveHomeState = entry.getValue();

            persistMilestoneEvent(homeId, liveHomeState);

            for (LiveApplianceState applianceState : liveHomeState.appliances().values()) {
                persistAnomalyState(homeId, applianceState);
            }
        }
    }

    private void persistMilestoneEvent(UUID homeId, LiveHomeState liveHomeState) {
        if (liveHomeState.highestMilestoneReached() == null) {
            return;
        }

        HomeBillingAccount billingAccount = homeBillingAccountRepository.findByHomeId(homeId).orElse(null);
        if (billingAccount == null) {
            return;
        }

        OffsetDateTime lastCapturedAt = LiveStateTimeCodec.toOffsetDateTime(liveHomeState.lastCapturedAt());
        LocalDate usageDate = lastCapturedAt != null
                ? lastCapturedAt.toLocalDate()
                : LocalDate.now(ZoneOffset.UTC);

        boolean exists = homeMilestoneEventRepository
                .findFirstByHomeIdAndBillingCycleStartedOnAndMilestoneOrderByTriggeredAtDesc(
                        homeId,
                        billingAccount.getCurrentCycleStartedOn(),
                        liveHomeState.highestMilestoneReached())
                .isPresent();

        if (!exists) {
            HomeMilestoneEvent event = new HomeMilestoneEvent();
            event.setHomeId(homeId);
            event.setBillingAccountId(billingAccount.getId());
            event.setBillingCycleStartedOn(billingAccount.getCurrentCycleStartedOn());
            event.setUsageDate(usageDate);
            event.setMilestone(liveHomeState.highestMilestoneReached());
            event.setStage(liveHomeState.highestMilestoneStage());
            event.setUsagePercentageOfLimit(calculateUsagePercentage(
                    liveHomeState.currentCycleUsageKwh(),
                    homeTariffPlanRepository.findByHomeId(homeId).map(HomeTariffPlan::getMonthlyUsageLimitKwh).orElse(null)));
            event.setTriggeredAt(lastCapturedAt != null ? lastCapturedAt : OffsetDateTime.now(ZoneOffset.UTC));
            homeMilestoneEventRepository.save(event);
        }
    }

    private void persistAnomalyState(UUID homeId, LiveApplianceState applianceState) {
        if (applianceState.anomalyActive()) {
            applianceAnomalyRepository.findFirstByApplianceIdAndStatusOrderByStartedAtDesc(
                    applianceState.applianceId(),
                    ApplianceAnomalyStatus.OPEN
            ).map(anomaly -> updateOpenAnomaly(anomaly, applianceState))
                    .orElseGet(() -> {
                ApplianceAnomaly anomaly = new ApplianceAnomaly();
                anomaly.setHomeId(homeId);
                anomaly.setApplianceId(applianceState.applianceId());
                anomaly.setAnomalyType(ApplianceAnomalyType.SAFE_LIMIT_BREACH);
                anomaly.setStatus(ApplianceAnomalyStatus.OPEN);
                anomaly.setStartedAt(applianceState.breachStartedAt() != null
                        ? LiveStateTimeCodec.toOffsetDateTime(applianceState.breachStartedAt())
                        : LiveStateTimeCodec.toOffsetDateTime(applianceState.lastCapturedAt()));
                anomaly.setBreachedSafeWattLimit(applianceState.safeWattLimit());
                anomaly.setAverageWatts(applianceState.latestWattage());
                anomaly.setPeakWatts(applianceState.latestWattage());
                anomaly.setConsecutiveBreachCount(applianceState.consecutiveBreachCount());
                anomaly.setNotes("Detected from Ignite live telemetry state after a continuous safe-limit breach.");
                return applianceAnomalyRepository.save(anomaly);
            });
            return;
        }

        applianceAnomalyRepository.findFirstByApplianceIdAndStatusOrderByStartedAtDesc(
                applianceState.applianceId(),
                ApplianceAnomalyStatus.OPEN
        ).ifPresent(anomaly -> {
            anomaly.setStatus(ApplianceAnomalyStatus.RESOLVED);
            OffsetDateTime lastCapturedAt = LiveStateTimeCodec.toOffsetDateTime(applianceState.lastCapturedAt());
            anomaly.setResolvedAt(lastCapturedAt);
            anomaly.setDurationSeconds((int) Duration.between(anomaly.getStartedAt(), lastCapturedAt).getSeconds());
            anomaly.setAverageWatts(applianceState.latestWattage());
            anomaly.setPeakWatts(max(anomaly.getPeakWatts(), applianceState.latestWattage()));
            anomaly.setConsecutiveBreachCount(Math.max(anomaly.getConsecutiveBreachCount(), applianceState.consecutiveBreachCount()));
            applianceAnomalyRepository.save(anomaly);
        });
    }

    private ApplianceAnomaly updateOpenAnomaly(ApplianceAnomaly anomaly, LiveApplianceState applianceState) {
        anomaly.setAverageWatts(applianceState.latestWattage());
        anomaly.setPeakWatts(max(anomaly.getPeakWatts(), applianceState.latestWattage()));
        anomaly.setConsecutiveBreachCount(Math.max(anomaly.getConsecutiveBreachCount(), applianceState.consecutiveBreachCount()));
        anomaly.setBreachedSafeWattLimit(applianceState.safeWattLimit());
        return applianceAnomalyRepository.save(anomaly);
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.max(right);
    }

    private BigDecimal calculateUsagePercentage(BigDecimal usageKwh, BigDecimal limitKwh) {
        if (usageKwh == null || limitKwh == null || limitKwh.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return usageKwh.multiply(BigDecimal.valueOf(100)).divide(limitKwh, 2, RoundingMode.HALF_UP);
    }

}
