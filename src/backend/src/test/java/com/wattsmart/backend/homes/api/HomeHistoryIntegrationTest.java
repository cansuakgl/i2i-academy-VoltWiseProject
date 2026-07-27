package com.wattsmart.backend.homes.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wattsmart.backend.homes.domain.HomeBillingCycle;
import com.wattsmart.backend.homes.domain.HomeUsageDaily;
import com.wattsmart.backend.homes.domain.HomeUsageMonthlySummary;
import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.SummaryPeriodType;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.homes.repository.HomeBillingCycleRepository;
import com.wattsmart.backend.homes.repository.HomeUsageDailyRepository;
import com.wattsmart.backend.homes.repository.HomeUsageMonthlySummaryRepository;
import com.wattsmart.backend.integration.IntegrationTestSupport;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomaly;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyStatus;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyType;
import com.wattsmart.backend.telemetry.persistence.domain.HomeMilestoneEvent;
import com.wattsmart.backend.telemetry.persistence.repository.ApplianceAnomalyRepository;
import com.wattsmart.backend.telemetry.persistence.repository.HomeMilestoneEventRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HomeHistoryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private HomeUsageDailyRepository homeUsageDailyRepository;

    @Autowired
    private HomeUsageMonthlySummaryRepository homeUsageMonthlySummaryRepository;

    @Autowired
    private HomeBillingCycleRepository homeBillingCycleRepository;

    @Autowired
    private HomeMilestoneEventRepository homeMilestoneEventRepository;

    @Autowired
    private ApplianceAnomalyRepository applianceAnomalyRepository;

    @Test
    void returnsModularHistoricalViewsForDailyMonthlyBillingMilestonesAndAnomalies() throws Exception {
        UserSession resident = registerAndLogin("history-resident");
        var tariffPlan = createTariffPlan("HISTORY", new BigDecimal("1.75"));
        var applianceType = createApplianceType("HISTORY-AC");
        RegisteredHome home = registerHome(resident.token(), tariffPlan, applianceType, new BigDecimal("200.000"));
        UUID applianceId = applianceRepository.findStatusAppliancesByHomeIds(List.of(home.homeId())).getFirst().getId();

        LocalDate usageDate = LocalDate.of(2026, 7, 10);
        saveDailyUsage(home.homeId(), usageDate);
        saveMonthlySummary(home.homeId(), usageDate.withDayOfMonth(1));
        saveBillingCycle(home.homeId(), tariffPlan.getId(), usageDate.withDayOfMonth(1));
        saveMilestone(home.homeId(), usageDate);
        saveAnomaly(home.homeId(), applianceId, usageDate);

        String fromDate = "2026-07-01";
        String toDate = "2026-07-31";

        mockMvc.perform(get("/api/homes/{homeId}/history/daily-usage", home.homeId())
                        .header("Authorization", "Bearer " + resident.token())
                        .param("fromDate", fromDate)
                        .param("toDate", toDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeId").value(home.homeId().toString()))
                .andExpect(jsonPath("$.dailyUsage[0].usageDate").value("2026-07-10"))
                .andExpect(jsonPath("$.dailyUsage[0].milestoneReached").value("PCT_100"));

        mockMvc.perform(get("/api/homes/{homeId}/history/monthly-usage", home.homeId())
                        .header("Authorization", "Bearer " + resident.token())
                        .param("fromDate", fromDate)
                        .param("toDate", toDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlySummaries[0].monthStart").value("2026-07-01"))
                .andExpect(jsonPath("$.monthlySummaries[0].highestMilestoneReached").value("PCT_120"));

        mockMvc.perform(get("/api/homes/{homeId}/history/billing-cycles", home.homeId())
                        .header("Authorization", "Bearer " + resident.token())
                        .param("fromDate", fromDate)
                        .param("toDate", toDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingCycles[0].tariffPlanId").value(tariffPlan.getId().toString()))
                .andExpect(jsonPath("$.billingCycles[0].appliedTariffCode").value("HISTORY-SNAPSHOT"));

        mockMvc.perform(get("/api/homes/{homeId}/history/milestones", home.homeId())
                        .header("Authorization", "Bearer " + resident.token())
                        .param("fromDate", fromDate)
                        .param("toDate", toDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestoneEvents[0].milestone").value("PCT_120"))
                .andExpect(jsonPath("$.milestoneEvents[0].stage").value("PENALTY"));

        mockMvc.perform(get("/api/homes/{homeId}/history/anomalies", home.homeId())
                        .header("Authorization", "Bearer " + resident.token())
                        .param("fromDate", fromDate)
                        .param("toDate", toDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applianceAnomalies[0].applianceId").value(applianceId.toString()))
                .andExpect(jsonPath("$.applianceAnomalies[0].status").value("RESOLVED"));
    }

    private void saveDailyUsage(UUID homeId, LocalDate usageDate) {
        HomeUsageDaily daily = new HomeUsageDaily();
        daily.setHomeId(homeId);
        daily.setUsageDate(usageDate);
        daily.setTotalEnergyKwh(new BigDecimal("12.500000"));
        daily.setAverageWatts(new BigDecimal("520.00"));
        daily.setPeakWatts(new BigDecimal("900.00"));
        daily.setUsagePercentageOfLimit(new BigDecimal("100.00"));
        daily.setMilestoneReached(UsagePercentageMilestone.PCT_100);
        daily.setMilestoneStage(MilestoneStage.WARNING);
        daily.setBaseCostAmount(new BigDecimal("21.88"));
        daily.setPenaltyCostAmount(new BigDecimal("0.00"));
        daily.setTotalCostAmount(new BigDecimal("21.88"));
        daily.setSampleCount(48);
        homeUsageDailyRepository.save(daily);
    }

    private void saveMonthlySummary(UUID homeId, LocalDate monthStart) {
        HomeUsageMonthlySummary summary = new HomeUsageMonthlySummary();
        summary.setHomeId(homeId);
        summary.setPeriodType(SummaryPeriodType.MONTHLY);
        summary.setMonthStart(monthStart);
        summary.setMonthEnd(monthStart.plusMonths(1).minusDays(1));
        summary.setTotalEnergyKwh(new BigDecimal("240.000000"));
        summary.setAverageDailyKwh(new BigDecimal("8.000000"));
        summary.setPeakDailyKwh(new BigDecimal("18.000000"));
        summary.setTotalBaseCostAmount(new BigDecimal("420.00"));
        summary.setTotalPenaltyCostAmount(new BigDecimal("35.00"));
        summary.setTotalCostAmount(new BigDecimal("455.00"));
        summary.setHighestMilestoneReached(UsagePercentageMilestone.PCT_120);
        summary.setHighestMilestoneStage(MilestoneStage.PENALTY);
        summary.setDaysCounted(30);
        homeUsageMonthlySummaryRepository.save(summary);
    }

    private void saveBillingCycle(UUID homeId, UUID tariffPlanId, LocalDate cycleStartedOn) {
        HomeBillingCycle cycle = new HomeBillingCycle();
        cycle.setHomeId(homeId);
        cycle.setTariffPlanId(tariffPlanId);
        cycle.setCycleStartedOn(cycleStartedOn);
        cycle.setCycleEndedOn(cycleStartedOn.plusMonths(1).minusDays(1));
        cycle.setBillingCycleStartDay((short) 1);
        cycle.setUsageLimitKwh(new BigDecimal("200.000"));
        cycle.setTotalUsageKwh(new BigDecimal("240.000000"));
        cycle.setTotalBaseCostAmount(new BigDecimal("420.00"));
        cycle.setTotalPenaltyCostAmount(new BigDecimal("35.00"));
        cycle.setTotalCostAmount(new BigDecimal("455.00"));
        cycle.setHighestMilestoneReached(UsagePercentageMilestone.PCT_120);
        cycle.setHighestMilestoneStage(MilestoneStage.PENALTY);
        cycle.setAppliedTariffCode("HISTORY-SNAPSHOT");
        cycle.setAppliedTariffName("History Snapshot");
        cycle.setAppliedCurrencyCode("TRY");
        cycle.setAppliedBaseRatePerKwh(new BigDecimal("1.75"));
        cycle.setFinalizedAt(OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        homeBillingCycleRepository.save(cycle);
    }

    private void saveMilestone(UUID homeId, LocalDate usageDate) {
        HomeMilestoneEvent event = new HomeMilestoneEvent();
        event.setHomeId(homeId);
        event.setBillingCycleStartedOn(usageDate.withDayOfMonth(1));
        event.setUsageDate(usageDate);
        event.setMilestone(UsagePercentageMilestone.PCT_120);
        event.setStage(MilestoneStage.PENALTY);
        event.setUsagePercentageOfLimit(new BigDecimal("120.00"));
        event.setTriggeredAt(usageDate.atTime(12, 0).atOffset(ZoneOffset.UTC));
        homeMilestoneEventRepository.save(event);
    }

    private void saveAnomaly(UUID homeId, UUID applianceId, LocalDate usageDate) {
        ApplianceAnomaly anomaly = new ApplianceAnomaly();
        anomaly.setHomeId(homeId);
        anomaly.setApplianceId(applianceId);
        anomaly.setAnomalyType(ApplianceAnomalyType.SAFE_LIMIT_BREACH);
        anomaly.setStatus(ApplianceAnomalyStatus.RESOLVED);
        anomaly.setStartedAt(usageDate.atTime(9, 0).atOffset(ZoneOffset.UTC));
        anomaly.setResolvedAt(usageDate.atTime(9, 3).atOffset(ZoneOffset.UTC));
        anomaly.setBreachedSafeWattLimit(new BigDecimal("300.00"));
        anomaly.setAverageWatts(new BigDecimal("620.00"));
        anomaly.setPeakWatts(new BigDecimal("710.00"));
        anomaly.setConsecutiveBreachCount(36);
        anomaly.setDurationSeconds(180);
        anomaly.setNotes("Historical API test anomaly");
        applianceAnomalyRepository.save(anomaly);
    }
}
