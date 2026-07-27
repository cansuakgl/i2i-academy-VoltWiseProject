package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.homes.domain.Appliance;
import com.wattsmart.backend.homes.domain.ApplianceType;
import com.wattsmart.backend.homes.domain.Home;
import com.wattsmart.backend.homes.domain.HomeBillingAccount;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.homes.repository.HomeBillingAccountRepository;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.telemetry.live.LiveApplianceState;
import com.wattsmart.backend.telemetry.live.LiveHomeState;
import com.wattsmart.backend.telemetry.live.LiveHomeStateStore;
import com.wattsmart.backend.telemetry.live.LiveStateTimeCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeLiveStateSyncService {

    private static final BigDecimal ZERO_USAGE = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_WATTS = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final HomeRepository homeRepository;
    private final ApplianceRepository applianceRepository;
    private final HomeBillingAccountRepository homeBillingAccountRepository;
    private final LiveHomeStateStore liveHomeStateStore;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void bootstrapLiveDashboardState() {
        List<Home> homes = homeRepository.findAllByOrderByNameAsc();
        List<UUID> homeIds = homes.stream().map(Home::getId).toList();
        if (homeIds.isEmpty()) {
            return;
        }

        Map<UUID, HomeBillingAccount> billingByHomeId = homeBillingAccountRepository.findByHomeIdIn(homeIds)
                .stream()
                .collect(Collectors.toMap(account -> account.getHome().getId(), account -> account));
        Map<UUID, List<Appliance>> appliancesByHomeId = applianceRepository.findStatusAppliancesByHomeIds(homeIds)
                .stream()
                .collect(Collectors.groupingBy(appliance -> appliance.getHome().getId()));

        homes.forEach(home -> syncHome(
                home,
                billingByHomeId.get(home.getId()),
                appliancesByHomeId.getOrDefault(home.getId(), List.of())));
    }

    public void syncHome(UUID homeId) {
        Home home = homeRepository.findById(homeId).orElse(null);
        if (home == null) {
            return;
        }
        HomeBillingAccount billingAccount = homeBillingAccountRepository.findByHomeId(homeId).orElse(null);
        List<Appliance> appliances = applianceRepository.findStatusAppliancesByHomeIds(List.of(homeId));
        syncHome(home, billingAccount, appliances);
    }

    public void syncHome(Home home, HomeBillingAccount billingAccount, List<Appliance> appliances) {
        LiveHomeState existing = liveHomeStateStore.getHomeState(home.getId());
        Map<UUID, LiveApplianceState> existingAppliances = existing != null ? existing.appliances() : Map.of();
        Map<UUID, LiveApplianceState> updatedAppliances = appliances.stream()
                .map(appliance -> toLiveApplianceState(appliance, existingAppliances.get(appliance.getId())))
                .collect(Collectors.toMap(LiveApplianceState::applianceId, item -> item));

        liveHomeStateStore.saveHomeState(new LiveHomeState(
                home.getId(),
                home.getExternalKey(),
                home.getName(),
                home.getStatus(),
                home.getTimezoneName(),
                billingAccount != null ? LiveStateTimeCodec.toIso(billingAccount.getCurrentCycleStartedOn()) : null,
                billingAccount != null ? LiveStateTimeCodec.toIso(billingAccount.getCurrentCycleEndsOn()) : null,
                existing != null ? existing.lastCapturedAt() : null,
                existing != null ? existing.totalInstantWatts() : ZERO_WATTS,
                existing != null ? existing.currentCycleUsageKwh() : ZERO_USAGE,
                existing != null ? existing.currentCycleBaseCostAmount() : ZERO_AMOUNT,
                existing != null ? existing.currentCyclePenaltyCostAmount() : ZERO_AMOUNT,
                existing != null ? existing.totalCostAmount() : ZERO_AMOUNT,
                existing != null ? existing.highestMilestoneReached() : null,
                existing != null ? existing.highestMilestoneStage() : null,
                new HashMap<>(updatedAppliances)));
    }

    private LiveApplianceState toLiveApplianceState(Appliance appliance, LiveApplianceState existing) {
        ApplianceType applianceType = appliance.getApplianceType();
        return new LiveApplianceState(
                appliance.getId(),
                appliance.getApplianceCode(),
                appliance.getName(),
                applianceType.getCode(),
                applianceType.getDisplayName(),
                defaultIfNull(appliance.getNominalWattage(), applianceType.getTypicalWatts()),
                existing != null ? existing.latestWattage() : null,
                defaultIfNull(appliance.getSafeWattLimit(), applianceType.getDefaultSafeWattLimit()),
                existing != null && existing.aboveSafeLimit(),
                existing != null ? existing.consecutiveBreachCount() : 0,
                existing != null ? existing.breachStartedAt() : null,
                existing != null && existing.anomalyActive(),
                existing != null ? existing.lastCapturedAt() : null,
                existing != null ? existing.currentUsageWindow() : null,
                existing != null ? existing.completedUsageWindow() : null,
                appliance.isActive());
    }

    private BigDecimal defaultIfNull(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }
}
