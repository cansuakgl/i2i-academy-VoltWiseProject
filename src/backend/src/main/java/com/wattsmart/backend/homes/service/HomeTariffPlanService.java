package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.service.AuthorizationService;
import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import com.wattsmart.backend.homes.api.dto.AssignHomeTariffPlanRequest;
import com.wattsmart.backend.homes.api.dto.HomeTariffPlanResponse;
import com.wattsmart.backend.homes.domain.Home;
import com.wattsmart.backend.homes.domain.HomeTariffPlan;
import com.wattsmart.backend.homes.domain.TariffPlan;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.homes.repository.HomeTariffPlanRepository;
import com.wattsmart.backend.homes.repository.TariffPlanRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeTariffPlanService {

    private static final short DEFAULT_BILLING_CYCLE_START_DAY = 1;

    private final AuthorizationService authorizationService;
    private final HomeRepository homeRepository;
    private final TariffPlanRepository tariffPlanRepository;
    private final HomeTariffPlanRepository homeTariffPlanRepository;

    @Transactional(readOnly = true)
    public List<HomeTariffPlanResponse> getTariffHistory(AppUser currentUser, UUID homeId) {
        authorizationService.requireAdmin(currentUser);
        requireHome(homeId);
        return homeTariffPlanRepository.findHistoryByHomeId(homeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public HomeTariffPlanResponse assignTariffPlan(
            AppUser currentUser,
            UUID homeId,
            AssignHomeTariffPlanRequest request
    ) {
        authorizationService.requireAdmin(currentUser);

        Home home = requireHome(homeId);
        TariffPlan tariffPlan = tariffPlanRepository.findById(request.tariffPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Tariff plan not found: " + request.tariffPlanId()));
        if (request.billingCycleStartDay() != null
                && (request.billingCycleStartDay() < 1 || request.billingCycleStartDay() > 28)) {
            throw new BadRequestException("Billing cycle start day must be between 1 and 28.");
        }
        LocalDate effectiveFrom = request.effectiveFrom() != null
                ? request.effectiveFrom()
                : LocalDate.now(ZoneId.of(home.getTimezoneName()));

        homeTariffPlanRepository.findByHomeId(homeId).ifPresent(current -> {
            if (!effectiveFrom.isAfter(current.getEffectiveFrom())) {
                throw new BadRequestException("New tariff effective date must be after the current tariff effective date.");
            }
            current.setEffectiveTo(effectiveFrom.minusDays(1));
            homeTariffPlanRepository.saveAndFlush(current);
        });

        HomeTariffPlan next = new HomeTariffPlan();
        next.setHome(home);
        next.setTariffPlan(tariffPlan);
        next.setMonthlyUsageLimitKwh(request.monthlyUsageLimitKwh());
        next.setBillingCycleStartDay(request.billingCycleStartDay() != null
                ? request.billingCycleStartDay()
                : DEFAULT_BILLING_CYCLE_START_DAY);
        next.setEffectiveFrom(effectiveFrom);
        next.setEffectiveTo(null);
        return toResponse(homeTariffPlanRepository.save(next));
    }

    private Home requireHome(UUID homeId) {
        return homeRepository.findById(homeId)
                .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + homeId));
    }

    private HomeTariffPlanResponse toResponse(HomeTariffPlan homeTariffPlan) {
        return new HomeTariffPlanResponse(
                homeTariffPlan.getId(),
                homeTariffPlan.getHome().getId(),
                homeTariffPlan.getTariffPlan().getId(),
                homeTariffPlan.getTariffPlan().getCode(),
                homeTariffPlan.getTariffPlan().getName(),
                homeTariffPlan.getMonthlyUsageLimitKwh(),
                homeTariffPlan.getBillingCycleStartDay(),
                homeTariffPlan.getEffectiveFrom(),
                homeTariffPlan.getEffectiveTo()
        );
    }
}
