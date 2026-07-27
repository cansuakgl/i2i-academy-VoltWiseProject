package com.wattsmart.backend.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.domain.HomeTariffPlan;
import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.TariffPlan;
import com.wattsmart.backend.homes.domain.TariffPlanMilestone;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import com.wattsmart.backend.homes.repository.HomeTariffPlanRepository;
import com.wattsmart.backend.homes.repository.TariffPlanMilestoneRepository;
import com.wattsmart.backend.telemetry.events.ApplianceTelemetryEvent;
import com.wattsmart.backend.telemetry.live.LiveHomeState;
import com.wattsmart.backend.telemetry.live.LiveHomeStateStore;
import com.wattsmart.backend.telemetry.live.LiveStateTimeCodec;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TelemetryProcessingServiceTest {

    private final HomeTariffPlanRepository homeTariffPlanRepository = mock(HomeTariffPlanRepository.class);
    private final TariffPlanMilestoneRepository tariffPlanMilestoneRepository = mock(TariffPlanMilestoneRepository.class);
    private final LiveHomeStateStore liveHomeStateStore = mock(LiveHomeStateStore.class);
    private final TelemetryProcessingService service = new TelemetryProcessingService(
            homeTariffPlanRepository,
            tariffPlanMilestoneRepository,
            liveHomeStateStore,
            new HomeTelemetryLockService());

    @Test
    void splitsPenaltyCostWhenUsageJumpsAcrossPenaltyThreshold() {
        UUID homeId = UUID.randomUUID();
        UUID tariffPlanId = UUID.randomUUID();
        UUID applianceId = UUID.randomUUID();
        OffsetDateTime previousCapture = OffsetDateTime.parse("2026-07-26T10:00:00Z");
        OffsetDateTime currentCapture = previousCapture.plusHours(1);

        when(homeTariffPlanRepository.findByHomeId(homeId)).thenReturn(Optional.of(homeTariffPlan(tariffPlanId)));
        when(tariffPlanMilestoneRepository.findByTariffPlanId(tariffPlanId)).thenReturn(List.of(
                milestone(UsagePercentageMilestone.PCT_100, MilestoneStage.WARNING, null),
                milestone(UsagePercentageMilestone.PCT_120, MilestoneStage.PENALTY, "2.00"),
                milestone(UsagePercentageMilestone.PCT_130, MilestoneStage.PENALTY, "3.00")));
        when(liveHomeStateStore.getHomeState(homeId)).thenReturn(previousState(homeId, previousCapture));

        service.process(new ApplianceTelemetryEvent(
                UUID.randomUUID(),
                homeId,
                "home-1",
                currentCapture,
                List.of(new ApplianceTelemetryEvent.ApplianceReading(
                        applianceId,
                        "fridge-1",
                        "FRIDGE",
                        new BigDecimal("22000.00"),
                        new BigDecimal("30000.00"),
                        false))));

        ArgumentCaptor<LiveHomeState> stateCaptor = ArgumentCaptor.forClass(LiveHomeState.class);
        verify(liveHomeStateStore).saveHomeState(stateCaptor.capture());

        LiveHomeState savedState = stateCaptor.getValue();
        assertThat(savedState.currentCycleUsageKwh()).isEqualByComparingTo("121.000000");
        assertThat(savedState.currentCycleBaseCostAmount()).isEqualByComparingTo("22.000000");
        assertThat(savedState.currentCyclePenaltyCostAmount()).isEqualByComparingTo("1.000000");
        assertThat(savedState.totalCostAmount()).isEqualByComparingTo("23.000000");
        assertThat(savedState.highestMilestoneReached()).isEqualTo(UsagePercentageMilestone.PCT_120);
        assertThat(savedState.highestMilestoneStage()).isEqualTo(MilestoneStage.PENALTY);
    }

    private HomeTariffPlan homeTariffPlan(UUID tariffPlanId) {
        TariffPlan tariffPlan = new TariffPlan();
        tariffPlan.setId(tariffPlanId);
        tariffPlan.setBaseRatePerKwh(new BigDecimal("1.00"));

        HomeTariffPlan homeTariffPlan = new HomeTariffPlan();
        homeTariffPlan.setTariffPlan(tariffPlan);
        homeTariffPlan.setMonthlyUsageLimitKwh(new BigDecimal("100.00"));
        return homeTariffPlan;
    }

    private TariffPlanMilestone milestone(
            UsagePercentageMilestone usagePercentageMilestone,
            MilestoneStage stage,
            String penaltyMultiplier
    ) {
        TariffPlanMilestone milestone = new TariffPlanMilestone();
        milestone.setMilestone(usagePercentageMilestone);
        milestone.setStage(stage);
        if (penaltyMultiplier != null) {
            milestone.setPenaltyMultiplier(new BigDecimal(penaltyMultiplier));
        }
        return milestone;
    }

    private LiveHomeState previousState(UUID homeId, OffsetDateTime previousCapture) {
        return new LiveHomeState(
                homeId,
                "home-1",
                "Home 1",
                HomeStatus.ACTIVE,
                "Europe/Istanbul",
                null,
                null,
                LiveStateTimeCodec.toIso(previousCapture),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("99.000000"),
                BigDecimal.ZERO.setScale(6),
                BigDecimal.ZERO.setScale(6),
                BigDecimal.ZERO.setScale(6),
                null,
                null,
                Map.of());
    }
}
