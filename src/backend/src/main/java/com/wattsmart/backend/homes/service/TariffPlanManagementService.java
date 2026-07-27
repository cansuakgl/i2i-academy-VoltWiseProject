package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.service.AuthorizationService;
import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import com.wattsmart.backend.homes.api.dto.TariffPlanManagementRequest;
import com.wattsmart.backend.homes.api.dto.TariffPlanMilestoneRequest;
import com.wattsmart.backend.homes.api.dto.TariffPlanResponse;
import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.TariffPlan;
import com.wattsmart.backend.homes.domain.TariffPlanMilestone;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import com.wattsmart.backend.homes.repository.TariffPlanMilestoneRepository;
import com.wattsmart.backend.homes.repository.TariffPlanRepository;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TariffPlanManagementService {

    private final AuthorizationService authorizationService;
    private final TariffPlanRepository tariffPlanRepository;
    private final TariffPlanMilestoneRepository tariffPlanMilestoneRepository;

    @Transactional(readOnly = true)
    public List<TariffPlanResponse> listTariffPlans(AppUser currentUser) {
        authorizationService.requireAdmin(currentUser);
        return tariffPlanRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TariffPlanResponse getTariffPlan(AppUser currentUser, UUID tariffPlanId) {
        authorizationService.requireAdmin(currentUser);
        return toResponse(requireTariffPlan(tariffPlanId));
    }

    @Transactional
    public TariffPlanResponse createTariffPlan(AppUser currentUser, TariffPlanManagementRequest request) {
        authorizationService.requireAdmin(currentUser);
        validateEffectiveDates(request);
        String code = normalizeCode(request.code());
        if (tariffPlanRepository.existsByCode(code)) {
            throw new BadRequestException("Tariff plan code already exists: " + code);
        }

        TariffPlan tariffPlan = new TariffPlan();
        tariffPlan.setCode(code);
        applyRequest(tariffPlan, request);
        TariffPlan saved = tariffPlanRepository.saveAndFlush(tariffPlan);
        replaceMilestones(saved, request.milestones());
        return toResponse(saved);
    }

    @Transactional
    public TariffPlanResponse updateTariffPlan(
            AppUser currentUser,
            UUID tariffPlanId,
            TariffPlanManagementRequest request
    ) {
        authorizationService.requireAdmin(currentUser);
        validateEffectiveDates(request);
        TariffPlan tariffPlan = requireTariffPlan(tariffPlanId);
        String code = normalizeCode(request.code());
        tariffPlanRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(tariffPlanId))
                .ifPresent(existing -> {
                    throw new BadRequestException("Tariff plan code already exists: " + code);
                });

        tariffPlan.setCode(code);
        applyRequest(tariffPlan, request);
        TariffPlan saved = tariffPlanRepository.saveAndFlush(tariffPlan);
        replaceMilestones(saved, request.milestones());
        return toResponse(saved);
    }

    @Transactional
    public TariffPlanResponse replaceMilestones(
            AppUser currentUser,
            UUID tariffPlanId,
            List<TariffPlanMilestoneRequest> milestones
    ) {
        authorizationService.requireAdmin(currentUser);
        TariffPlan tariffPlan = requireTariffPlan(tariffPlanId);
        replaceMilestones(tariffPlan, milestones);
        return toResponse(tariffPlan);
    }

    @Transactional
    public TariffPlanResponse deactivateTariffPlan(AppUser currentUser, UUID tariffPlanId) {
        authorizationService.requireAdmin(currentUser);
        TariffPlan tariffPlan = requireTariffPlan(tariffPlanId);
        tariffPlan.setActive(false);
        return toResponse(tariffPlanRepository.save(tariffPlan));
    }

    @Transactional
    public void deleteTariffPlan(AppUser currentUser, UUID tariffPlanId) {
        authorizationService.requireAdmin(currentUser);
        TariffPlan tariffPlan = requireTariffPlan(tariffPlanId);
        try {
            tariffPlanMilestoneRepository.deleteByTariffPlanId(tariffPlan.getId());
            tariffPlanMilestoneRepository.flush();
            tariffPlanRepository.delete(tariffPlan);
            tariffPlanRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("Tariff plan is already used by homes or billing history. Deactivate it instead.");
        }
    }

    private void applyRequest(TariffPlan tariffPlan, TariffPlanManagementRequest request) {
        tariffPlan.setName(request.name());
        tariffPlan.setDescription(request.description());
        tariffPlan.setCurrencyCode(request.currencyCode().toUpperCase());
        tariffPlan.setBaseRatePerKwh(request.baseRatePerKwh());
        tariffPlan.setEffectiveFrom(request.effectiveFrom());
        tariffPlan.setEffectiveTo(request.effectiveTo());
        tariffPlan.setActive(request.active() == null || request.active());
    }

    private void replaceMilestones(TariffPlan tariffPlan, List<TariffPlanMilestoneRequest> milestoneRequests) {
        List<TariffPlanMilestoneRequest> requests = milestoneRequests != null ? milestoneRequests : List.of();
        validateMilestones(requests);
        tariffPlanMilestoneRepository.deleteByTariffPlanId(tariffPlan.getId());
        tariffPlanMilestoneRepository.flush();

        for (TariffPlanMilestoneRequest request : requests) {
            TariffPlanMilestone milestone = new TariffPlanMilestone();
            milestone.setTariffPlan(tariffPlan);
            milestone.setMilestone(request.milestone());
            milestone.setStage(request.stage());
            milestone.setPenaltyMultiplier(request.penaltyMultiplier());
            tariffPlanMilestoneRepository.save(milestone);
        }
    }

    private void validateMilestones(List<TariffPlanMilestoneRequest> milestones) {
        EnumSet<UsagePercentageMilestone> seen = EnumSet.noneOf(UsagePercentageMilestone.class);
        for (TariffPlanMilestoneRequest milestone : milestones) {
            if (!seen.add(milestone.milestone())) {
                throw new BadRequestException("Duplicate tariff milestone: " + milestone.milestone());
            }
            if (milestone.stage() == MilestoneStage.WARNING && milestone.penaltyMultiplier() != null) {
                throw new BadRequestException("Warning milestones cannot have a penalty multiplier.");
            }
            if (milestone.stage() == MilestoneStage.PENALTY && milestone.penaltyMultiplier() == null) {
                throw new BadRequestException("Penalty milestones require a penalty multiplier.");
            }
        }
    }

    private void validateEffectiveDates(TariffPlanManagementRequest request) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new BadRequestException("Tariff effective_to must be on or after effective_from.");
        }
    }

    private TariffPlan requireTariffPlan(UUID tariffPlanId) {
        return tariffPlanRepository.findById(tariffPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Tariff plan not found: " + tariffPlanId));
    }

    private TariffPlanResponse toResponse(TariffPlan tariffPlan) {
        List<TariffPlanResponse.TariffPlanMilestoneItem> milestones = tariffPlanMilestoneRepository
                .findByTariffPlanId(tariffPlan.getId())
                .stream()
                .sorted(Comparator.comparingInt(milestone -> thresholdFor(milestone.getMilestone())))
                .map(milestone -> new TariffPlanResponse.TariffPlanMilestoneItem(
                        milestone.getId(),
                        milestone.getMilestone(),
                        milestone.getStage(),
                        milestone.getPenaltyMultiplier()))
                .toList();

        return new TariffPlanResponse(
                tariffPlan.getId(),
                tariffPlan.getCode(),
                tariffPlan.getName(),
                tariffPlan.getDescription(),
                tariffPlan.getCurrencyCode(),
                tariffPlan.getBaseRatePerKwh(),
                tariffPlan.getEffectiveFrom(),
                tariffPlan.getEffectiveTo(),
                tariffPlan.isActive(),
                milestones
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
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
}
