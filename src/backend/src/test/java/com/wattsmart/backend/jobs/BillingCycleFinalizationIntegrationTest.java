package com.wattsmart.backend.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.wattsmart.backend.homes.domain.HomeBillingAccount;
import com.wattsmart.backend.homes.domain.HomeBillingCycle;
import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import com.wattsmart.backend.homes.repository.HomeBillingAccountRepository;
import com.wattsmart.backend.homes.repository.HomeBillingCycleRepository;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.integration.IntegrationTestSupport;
import com.wattsmart.backend.telemetry.live.LiveHomeState;
import com.wattsmart.backend.telemetry.live.LiveHomeStateStore;
import com.wattsmart.backend.telemetry.live.LiveStateTimeCodec;
import com.wattsmart.backend.telemetry.persistence.domain.HomeMilestoneEvent;
import com.wattsmart.backend.telemetry.persistence.repository.HomeMilestoneEventRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BillingCycleFinalizationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private BillingCycleFinalizationService billingCycleFinalizationService;

    @Autowired
    private HomeBillingAccountRepository homeBillingAccountRepository;

    @Autowired
    private HomeBillingCycleRepository homeBillingCycleRepository;

    @Autowired
    private HomeMilestoneEventRepository homeMilestoneEventRepository;

    @Autowired
    private HomeRepository homeRepository;

    @Autowired
    private LiveHomeStateStore liveHomeStateStore;

    @Test
    void finalizesDueBillingCycleSnapshotsTariffLinksMilestonesAndResetsCurrentAccountAndLiveState() throws Exception {
        UserSession resident = registerAndLogin("billing-resident");
        var tariffPlan = createTariffPlan("BILLING", new BigDecimal("2.50"));
        var applianceType = createApplianceType("BILLING-FRIDGE");
        RegisteredHome home = registerHome(resident.token(), tariffPlan, applianceType, new BigDecimal("100.000"));

        LocalDate cycleStartedOn = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        HomeBillingAccount account = homeBillingAccountRepository.findByHomeId(home.homeId()).orElseThrow();
        account.setCurrentCycleStartedOn(cycleStartedOn);
        account.setCurrentCycleUsageKwh(new BigDecimal("145.000000"));
        account.setCurrentCycleBaseCostAmount(new BigDecimal("250.00"));
        account.setCurrentCyclePenaltyCostAmount(new BigDecimal("75.00"));
        account.setTotalCostAmount(new BigDecimal("325.00"));
        account.setHighestMilestoneReached(UsagePercentageMilestone.PCT_120);
        account.setHighestMilestoneStage(MilestoneStage.PENALTY);
        account = homeBillingAccountRepository.save(account);

        HomeMilestoneEvent milestoneEvent = new HomeMilestoneEvent();
        milestoneEvent.setHomeId(home.homeId());
        milestoneEvent.setBillingAccountId(account.getId());
        milestoneEvent.setBillingCycleStartedOn(cycleStartedOn);
        milestoneEvent.setUsageDate(cycleStartedOn.plusDays(10));
        milestoneEvent.setMilestone(UsagePercentageMilestone.PCT_120);
        milestoneEvent.setStage(MilestoneStage.PENALTY);
        milestoneEvent.setUsagePercentageOfLimit(new BigDecimal("145.00"));
        milestoneEvent.setTriggeredAt(cycleStartedOn.plusDays(10).atStartOfDay().atOffset(ZoneOffset.UTC));
        milestoneEvent = homeMilestoneEventRepository.save(milestoneEvent);

        liveHomeStateStore.saveHomeState(new LiveHomeState(
                home.homeId(),
                home.externalKey(),
                "Integration Home",
                HomeStatus.ACTIVE,
                "Europe/Istanbul",
                LiveStateTimeCodec.toIso(cycleStartedOn),
                null,
                LiveStateTimeCodec.toIso(cycleStartedOn.plusDays(20).atStartOfDay().atOffset(ZoneOffset.UTC)),
                new BigDecimal("400.00"),
                new BigDecimal("145.000000"),
                new BigDecimal("250.00"),
                new BigDecimal("75.00"),
                new BigDecimal("325.00"),
                UsagePercentageMilestone.PCT_120,
                MilestoneStage.PENALTY,
                Map.of()));

        int finalizedCount = billingCycleFinalizationService.finalizeDueBillingCycles();

        assertThat(finalizedCount).isEqualTo(1);
        HomeBillingAccount rolledAccount = homeBillingAccountRepository.findByHomeId(home.homeId()).orElseThrow();
        assertThat(rolledAccount.getCurrentCycleStartedOn()).isAfter(cycleStartedOn);
        assertThat(rolledAccount.getCurrentCycleUsageKwh()).isEqualByComparingTo("0.000000");
        assertThat(rolledAccount.getCurrentCycleBaseCostAmount()).isEqualByComparingTo("0.00");
        assertThat(rolledAccount.getHighestMilestoneReached()).isNull();

        HomeBillingCycle finalizedCycle = homeBillingCycleRepository
                .findByHomeIdAndCycleStartedOnLessThanEqualAndCycleEndedOnGreaterThanEqualOrderByCycleStartedOnDesc(
                        home.homeId(),
                        cycleStartedOn,
                        cycleStartedOn)
                .getFirst();
        assertThat(finalizedCycle.getTariffPlanId()).isEqualTo(tariffPlan.getId());
        assertThat(finalizedCycle.getTotalUsageKwh()).isEqualByComparingTo("145.000000");
        assertThat(finalizedCycle.getTotalCostAmount()).isEqualByComparingTo("325.00");
        assertThat(finalizedCycle.getHighestMilestoneReached()).isEqualTo(UsagePercentageMilestone.PCT_120);
        assertThat(finalizedCycle.getAppliedTariffCode()).isEqualTo(tariffPlan.getCode());
        assertThat(finalizedCycle.getAppliedTariffName()).isEqualTo(tariffPlan.getName());
        assertThat(finalizedCycle.getAppliedCurrencyCode()).isEqualTo("TRY");
        assertThat(finalizedCycle.getAppliedBaseRatePerKwh()).isEqualByComparingTo("2.50");

        HomeMilestoneEvent linkedMilestone = homeMilestoneEventRepository.findById(milestoneEvent.getId()).orElseThrow();
        assertThat(linkedMilestone.getBillingCycleId()).isEqualTo(finalizedCycle.getId());

        LiveHomeState rolledLiveState = liveHomeStateStore.getHomeState(home.homeId());
        assertThat(LiveStateTimeCodec.toLocalDate(rolledLiveState.currentCycleStartedOn())).isEqualTo(rolledAccount.getCurrentCycleStartedOn());
        assertThat(rolledLiveState.currentCycleUsageKwh()).isEqualByComparingTo("0.000000");
        assertThat(rolledLiveState.totalCostAmount()).isEqualByComparingTo("0.00");

        assertThat(homeRepository.findById(home.homeId())).isPresent();
    }
}
