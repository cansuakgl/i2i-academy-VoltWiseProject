package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.service.AuthorizationService;
import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import com.wattsmart.backend.homes.api.dto.ApplianceManagementRequest;
import com.wattsmart.backend.homes.api.dto.ApplianceResponse;
import com.wattsmart.backend.homes.domain.Appliance;
import com.wattsmart.backend.homes.domain.ApplianceType;
import com.wattsmart.backend.homes.domain.Home;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.homes.repository.ApplianceTypeRepository;
import com.wattsmart.backend.homes.repository.HomeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplianceManagementService {

    private final AuthorizationService authorizationService;
    private final HomeRepository homeRepository;
    private final ApplianceRepository applianceRepository;
    private final ApplianceTypeRepository applianceTypeRepository;
    private final HomeLiveStateSyncService homeLiveStateSyncService;

    @Transactional(readOnly = true)
    public List<ApplianceResponse> listAppliances(AppUser currentUser, UUID homeId) {
        authorizationService.requireAdminOrOperator(currentUser);
        requireHome(homeId);
        return applianceRepository.findStatusAppliancesByHomeIds(List.of(homeId)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ApplianceResponse addAppliance(AppUser currentUser, UUID homeId, ApplianceManagementRequest request) {
        authorizationService.requireAdminOrOperator(currentUser);
        Home home = requireHome(homeId);
        ApplianceType applianceType = requireApplianceType(request.typeCode());
        if (applianceRepository.existsByHomeIdAndApplianceCode(homeId, request.applianceCode())) {
            throw new BadRequestException("Appliance code already exists for home: " + request.applianceCode());
        }

        Appliance appliance = new Appliance();
        appliance.setHome(home);
        appliance.setActive(true);
        applyRequest(appliance, applianceType, request);
        Appliance saved = applianceRepository.save(appliance);
        homeLiveStateSyncService.syncHome(homeId);
        return toResponse(saved);
    }

    @Transactional
    public ApplianceResponse updateAppliance(
            AppUser currentUser,
            UUID homeId,
            UUID applianceId,
            ApplianceManagementRequest request
    ) {
        authorizationService.requireAdminOrOperator(currentUser);
        requireHome(homeId);
        Appliance appliance = requireAppliance(homeId, applianceId);
        ApplianceType applianceType = requireApplianceType(request.typeCode());
        if (!appliance.getApplianceCode().equals(request.applianceCode())
                && applianceRepository.existsByHomeIdAndApplianceCode(homeId, request.applianceCode())) {
            throw new BadRequestException("Appliance code already exists for home: " + request.applianceCode());
        }

        applyRequest(appliance, applianceType, request);
        Appliance saved = applianceRepository.save(appliance);
        homeLiveStateSyncService.syncHome(homeId);
        return toResponse(saved);
    }

    @Transactional
    public ApplianceResponse deactivateAppliance(AppUser currentUser, UUID homeId, UUID applianceId) {
        authorizationService.requireAdminOrOperator(currentUser);
        requireHome(homeId);
        Appliance appliance = requireAppliance(homeId, applianceId);
        appliance.setActive(false);
        Appliance saved = applianceRepository.save(appliance);
        homeLiveStateSyncService.syncHome(homeId);
        return toResponse(saved);
    }

    private void applyRequest(Appliance appliance, ApplianceType applianceType, ApplianceManagementRequest request) {
        appliance.setApplianceType(applianceType);
        appliance.setApplianceCode(request.applianceCode());
        appliance.setName(request.name());
        appliance.setManufacturer(request.manufacturer());
        appliance.setModelName(request.modelName());
        appliance.setNominalWattage(request.nominalWattage());
        appliance.setSafeWattLimit(defaultIfNull(request.safeWattLimit(), applianceType.getDefaultSafeWattLimit()));
        appliance.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : (short) 0);
        appliance.setInstalledAt(request.installedAt());
    }

    private Home requireHome(UUID homeId) {
        return homeRepository.findById(homeId)
                .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + homeId));
    }

    private Appliance requireAppliance(UUID homeId, UUID applianceId) {
        return applianceRepository.findByHomeIdAndId(homeId, applianceId)
                .orElseThrow(() -> new ResourceNotFoundException("Appliance not found: " + applianceId));
    }

    private ApplianceType requireApplianceType(String typeCode) {
        return applianceTypeRepository.findByCode(typeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Appliance type not found: " + typeCode));
    }

    private ApplianceResponse toResponse(Appliance appliance) {
        ApplianceType applianceType = appliance.getApplianceType();
        return new ApplianceResponse(
                appliance.getId(),
                appliance.getHome().getId(),
                applianceType.getId(),
                applianceType.getCode(),
                applianceType.getDisplayName(),
                appliance.getApplianceCode(),
                appliance.getName(),
                appliance.getManufacturer(),
                appliance.getModelName(),
                appliance.getNominalWattage(),
                appliance.getSafeWattLimit(),
                appliance.getDisplayOrder(),
                appliance.isActive(),
                appliance.getInstalledAt()
        );
    }

    private BigDecimal defaultIfNull(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }
}
