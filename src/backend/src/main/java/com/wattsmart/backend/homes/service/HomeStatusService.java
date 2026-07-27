package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.service.AuthorizationService;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse.ApplianceStatusItem;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse.BillingStatus;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse.HomeStatusItem;
import com.wattsmart.backend.telemetry.live.LiveApplianceState;
import com.wattsmart.backend.telemetry.live.LiveHomeState;
import com.wattsmart.backend.telemetry.live.LiveHomeStateStore;
import com.wattsmart.backend.telemetry.live.LiveStateTimeCodec;
import com.wattsmart.backend.homes.repository.HomeUserMembershipRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeStatusService {

    private final HomeUserMembershipRepository homeUserMembershipRepository;
    private final AuthorizationService authorizationService;
    private final LiveHomeStateStore liveHomeStateStore;

    @Transactional(readOnly = true)
    public HomeStatusResponse getDashboardStatus(AppUser user) {
        Map<UUID, LiveHomeState> liveStatesByHomeId = authorizationService.hasGlobalHomeAccess(user)
                ? liveHomeStateStore.getAllHomeStates()
                : liveHomeStateStore.getHomeStates(homeUserMembershipRepository.findAcceptedHomeIdsByUserId(user.getId()));

        List<HomeStatusItem> items = liveStatesByHomeId.values().stream()
                .sorted(Comparator.comparing(LiveHomeState::homeName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toStatusItem)
                .toList();

        return new HomeStatusResponse(items);
    }

    private HomeStatusItem toStatusItem(LiveHomeState liveHomeState) {
        return new HomeStatusItem(
                liveHomeState.homeId(),
                liveHomeState.homeExternalKey(),
                liveHomeState.homeName(),
                liveHomeState.homeStatus(),
                liveHomeState.timezoneName(),
                toBillingStatus(liveHomeState),
                liveHomeState.appliances().values().stream()
                        .sorted(Comparator.comparing(LiveApplianceState::applianceName, Comparator.nullsLast(String::compareToIgnoreCase)))
                        .map(this::toApplianceStatus)
                        .toList()
        );
    }

    private BillingStatus toBillingStatus(LiveHomeState liveHomeState) {
        return new BillingStatus(
                LiveStateTimeCodec.toLocalDate(liveHomeState.currentCycleStartedOn()),
                LiveStateTimeCodec.toLocalDate(liveHomeState.currentCycleEndsOn()),
                liveHomeState.currentCycleUsageKwh(),
                liveHomeState.currentCycleBaseCostAmount(),
                liveHomeState.currentCyclePenaltyCostAmount(),
                liveHomeState.totalCostAmount(),
                liveHomeState.highestMilestoneReached(),
                liveHomeState.highestMilestoneStage(),
                liveHomeState.totalInstantWatts(),
                LiveStateTimeCodec.toOffsetDateTime(liveHomeState.lastCapturedAt()),
                null
        );
    }

    private ApplianceStatusItem toApplianceStatus(LiveApplianceState liveApplianceState) {
        return new ApplianceStatusItem(
                liveApplianceState.applianceId(),
                liveApplianceState.applianceCode(),
                liveApplianceState.applianceName(),
                liveApplianceState.applianceTypeCode(),
                liveApplianceState.applianceTypeDisplayName(),
                liveApplianceState.typicalWatts(),
                liveApplianceState.safeWattLimit(),
                liveApplianceState.latestWattage(),
                liveApplianceState.aboveSafeLimit(),
                liveApplianceState.consecutiveBreachCount(),
                liveApplianceState.anomalyActive(),
                LiveStateTimeCodec.toOffsetDateTime(liveApplianceState.lastCapturedAt()),
                liveApplianceState.active()
        );
    }
}
