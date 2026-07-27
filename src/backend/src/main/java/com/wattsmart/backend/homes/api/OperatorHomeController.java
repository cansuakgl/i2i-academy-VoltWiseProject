package com.wattsmart.backend.homes.api;

import com.wattsmart.backend.auth.service.AuthenticatedUserService;
import com.wattsmart.backend.auth.service.AuthorizationService;
import com.wattsmart.backend.homes.api.dto.HomeAnomalyHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeBillingCycleHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeDailyUsageHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeMilestoneHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeMonthlyUsageHistoryResponse;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse;
import com.wattsmart.backend.homes.service.HomeHistoryService;
import com.wattsmart.backend.homes.service.HomeStatusService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator/homes")
@RequiredArgsConstructor
public class OperatorHomeController {

    private final AuthenticatedUserService authenticatedUserService;
    private final AuthorizationService authorizationService;
    private final HomeStatusService homeStatusService;
    private final HomeHistoryService homeHistoryService;

    @GetMapping("/status")
    public HomeStatusResponse getAllHomeStatus() {
        var user = authenticatedUserService.requireCurrentUser();
        authorizationService.requireAdminOrOperator(user);
        return homeStatusService.getDashboardStatus(user);
    }

    @GetMapping("/{homeId}/history/billing-cycles")
    public HomeBillingCycleHistoryResponse getBillingCycleHistory(
            @PathVariable UUID homeId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        var user = authenticatedUserService.requireCurrentUser();
        authorizationService.requireAdminOrOperator(user);
        return homeHistoryService.getBillingCycleHistory(user, homeId, fromDate, toDate);
    }

    @GetMapping("/{homeId}/history/daily-usage")
    public HomeDailyUsageHistoryResponse getDailyUsageHistory(
            @PathVariable UUID homeId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        var user = authenticatedUserService.requireCurrentUser();
        authorizationService.requireAdminOrOperator(user);
        return homeHistoryService.getDailyUsageHistory(user, homeId, fromDate, toDate);
    }

    @GetMapping("/{homeId}/history/monthly-usage")
    public HomeMonthlyUsageHistoryResponse getMonthlyUsageHistory(
            @PathVariable UUID homeId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        var user = authenticatedUserService.requireCurrentUser();
        authorizationService.requireAdminOrOperator(user);
        return homeHistoryService.getMonthlyUsageHistory(user, homeId, fromDate, toDate);
    }

    @GetMapping("/{homeId}/history/milestones")
    public HomeMilestoneHistoryResponse getMilestoneHistory(
            @PathVariable UUID homeId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        var user = authenticatedUserService.requireCurrentUser();
        authorizationService.requireAdminOrOperator(user);
        return homeHistoryService.getMilestoneHistory(user, homeId, fromDate, toDate);
    }

    @GetMapping("/{homeId}/history/anomalies")
    public HomeAnomalyHistoryResponse getAnomalyHistory(
            @PathVariable UUID homeId,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate
    ) {
        var user = authenticatedUserService.requireCurrentUser();
        authorizationService.requireAdminOrOperator(user);
        return homeHistoryService.getAnomalyHistory(user, homeId, fromDate, toDate);
    }
}
