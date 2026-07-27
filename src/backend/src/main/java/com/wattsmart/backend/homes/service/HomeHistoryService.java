package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.service.AuthorizationService;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import com.wattsmart.backend.common.service.UnauthorizedException;
import com.wattsmart.backend.homes.api.dto.HomeAnomalyHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeBillingCycleHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeDailyUsageHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeHistoryResponse.ApplianceAnomalyItem;
import com.wattsmart.backend.homes.api.dto.HomeHistoryResponse.BillingCycleItem;
import com.wattsmart.backend.homes.api.dto.HomeHistoryResponse.DailyUsageItem;
import com.wattsmart.backend.homes.api.dto.HomeHistoryResponse.MilestoneEventItem;
import com.wattsmart.backend.homes.api.dto.HomeHistoryResponse.MonthlySummaryItem;
import com.wattsmart.backend.homes.api.dto.HomeMilestoneHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeMonthlyUsageHistoryResponse;
import com.wattsmart.backend.homes.domain.HomeBillingCycle;
import com.wattsmart.backend.homes.domain.HomeUsageDaily;
import com.wattsmart.backend.homes.domain.HomeUsageMonthlySummary;
import com.wattsmart.backend.homes.repository.HomeBillingCycleRepository;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.homes.repository.HomeUsageDailyRepository;
import com.wattsmart.backend.homes.repository.HomeUsageMonthlySummaryRepository;
import com.wattsmart.backend.telemetry.persistence.repository.ApplianceAnomalyRepository;
import com.wattsmart.backend.telemetry.persistence.repository.HomeMilestoneEventRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeHistoryService {

    private final HomeRepository homeRepository;
    private final AuthorizationService authorizationService;
    private final HomeBillingCycleRepository homeBillingCycleRepository;
    private final HomeUsageDailyRepository homeUsageDailyRepository;
    private final HomeUsageMonthlySummaryRepository homeUsageMonthlySummaryRepository;
    private final HomeMilestoneEventRepository homeMilestoneEventRepository;
    private final ApplianceAnomalyRepository applianceAnomalyRepository;

    @Transactional(readOnly = true)
    public HomeDailyUsageHistoryResponse getDailyUsageHistory(
            AppUser user,
            UUID homeId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        requireHomeAccess(user, homeId);
        return new HomeDailyUsageHistoryResponse(homeId, fromDate, toDate, dailyUsageFor(homeId, fromDate, toDate));
    }

    @Transactional(readOnly = true)
    public HomeMonthlyUsageHistoryResponse getMonthlyUsageHistory(
            AppUser user,
            UUID homeId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        requireHomeAccess(user, homeId);
        return new HomeMonthlyUsageHistoryResponse(homeId, fromDate, toDate, monthlySummariesFor(homeId, fromDate, toDate));
    }

    @Transactional(readOnly = true)
    public HomeBillingCycleHistoryResponse getBillingCycleHistory(
            AppUser user,
            UUID homeId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        requireHomeAccess(user, homeId);
        return new HomeBillingCycleHistoryResponse(
                homeId,
                fromDate,
                toDate,
                billingCyclesFor(homeId, fromDate, toDate)
        );
    }

    @Transactional(readOnly = true)
    public HomeMilestoneHistoryResponse getMilestoneHistory(
            AppUser user,
            UUID homeId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        requireHomeAccess(user, homeId);
        return new HomeMilestoneHistoryResponse(homeId, fromDate, toDate, milestoneEventsFor(homeId, fromDate, toDate));
    }

    @Transactional(readOnly = true)
    public HomeAnomalyHistoryResponse getAnomalyHistory(
            AppUser user,
            UUID homeId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        requireHomeAccess(user, homeId);
        return new HomeAnomalyHistoryResponse(homeId, fromDate, toDate, applianceAnomaliesFor(homeId, fromDate, toDate));
    }

    private void requireHomeAccess(AppUser user, UUID homeId) {
        if (!homeRepository.existsById(homeId)) {
            throw new ResourceNotFoundException("Home not found: " + homeId);
        }
        if (!authorizationService.canAccessHome(user, homeId)) {
            throw new UnauthorizedException("You do not have access to this home.");
        }
    }

    private List<DailyUsageItem> dailyUsageFor(UUID homeId, LocalDate fromDate, LocalDate toDate) {
        return homeUsageDailyRepository.findByHomeIdAndUsageDateBetweenOrderByUsageDateAsc(homeId, fromDate, toDate)
                .stream()
                .map(this::toDailyUsageItem)
                .toList();
    }

    private DailyUsageItem toDailyUsageItem(HomeUsageDaily item) {
        return new DailyUsageItem(
                item.getUsageDate(),
                item.getTotalEnergyKwh(),
                item.getAverageWatts(),
                item.getPeakWatts(),
                item.getUsagePercentageOfLimit(),
                item.getMilestoneReached(),
                item.getMilestoneStage(),
                item.getBaseCostAmount(),
                item.getPenaltyCostAmount(),
                item.getTotalCostAmount(),
                item.getSampleCount()
        );
    }

    private List<MonthlySummaryItem> monthlySummariesFor(UUID homeId, LocalDate fromDate, LocalDate toDate) {
        return homeUsageMonthlySummaryRepository
                .findByHomeIdAndMonthStartBetweenOrderByMonthStartAsc(homeId, fromDate.withDayOfMonth(1), toDate)
                .stream()
                .map(this::toMonthlySummaryItem)
                .toList();
    }

    private MonthlySummaryItem toMonthlySummaryItem(HomeUsageMonthlySummary item) {
        return new MonthlySummaryItem(
                item.getMonthStart(),
                item.getMonthEnd(),
                item.getTotalEnergyKwh(),
                item.getAverageDailyKwh(),
                item.getPeakDailyKwh(),
                item.getTotalBaseCostAmount(),
                item.getTotalPenaltyCostAmount(),
                item.getTotalCostAmount(),
                item.getHighestMilestoneReached(),
                item.getHighestMilestoneStage(),
                item.getDaysCounted()
        );
    }

    private List<BillingCycleItem> billingCyclesFor(UUID homeId, LocalDate fromDate, LocalDate toDate) {
        return homeBillingCycleRepository
                .findByHomeIdAndCycleStartedOnLessThanEqualAndCycleEndedOnGreaterThanEqualOrderByCycleStartedOnDesc(
                        homeId,
                        toDate,
                        fromDate)
                .stream()
                .map(this::toBillingCycleItem)
                .toList();
    }

    private List<MilestoneEventItem> milestoneEventsFor(UUID homeId, LocalDate fromDate, LocalDate toDate) {
        return homeMilestoneEventRepository
                .findByHomeIdAndTriggeredAtBetweenOrderByTriggeredAtAsc(homeId, startOfDay(fromDate), endOfDay(toDate))
                .stream()
                .map(item -> new MilestoneEventItem(
                        item.getMilestone(),
                        item.getStage(),
                        item.getUsagePercentageOfLimit(),
                        item.getUsageDate(),
                        item.getTriggeredAt()))
                .toList();
    }

    private List<ApplianceAnomalyItem> applianceAnomaliesFor(UUID homeId, LocalDate fromDate, LocalDate toDate) {
        return applianceAnomalyRepository.findOverlappingHomeAnomalies(homeId, startOfDay(fromDate), endOfDay(toDate))
                .stream()
                .map(item -> new ApplianceAnomalyItem(
                        item.getApplianceId(),
                        item.getAnomalyType(),
                        item.getStatus(),
                        item.getStartedAt(),
                        item.getResolvedAt(),
                        item.getBreachedSafeWattLimit(),
                        item.getAverageWatts(),
                        item.getPeakWatts(),
                        item.getConsecutiveBreachCount(),
                        item.getDurationSeconds(),
                        item.getNotificationSentAt(),
                        item.getNotes()))
                .toList();
    }

    private BillingCycleItem toBillingCycleItem(HomeBillingCycle item) {
        return new BillingCycleItem(
                item.getId(),
                item.getTariffPlanId(),
                item.getCycleStartedOn(),
                item.getCycleEndedOn(),
                item.getBillingCycleStartDay(),
                item.getUsageLimitKwh(),
                item.getTotalUsageKwh(),
                item.getTotalBaseCostAmount(),
                item.getTotalPenaltyCostAmount(),
                item.getTotalCostAmount(),
                item.getHighestMilestoneReached(),
                item.getHighestMilestoneStage(),
                item.getAppliedTariffCode(),
                item.getAppliedTariffName(),
                item.getAppliedCurrencyCode(),
                item.getAppliedBaseRatePerKwh(),
                item.getFinalizedAt()
        );
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
    }
}
