package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.homes.api.dto.RegistrationOptionsResponse;
import com.wattsmart.backend.homes.api.dto.RegistrationOptionsResponse.ApplianceModelProfileOption;
import com.wattsmart.backend.homes.api.dto.RegistrationOptionsResponse.ApplianceTypeOption;
import com.wattsmart.backend.homes.api.dto.RegistrationOptionsResponse.TariffPlanOption;
import com.wattsmart.backend.homes.repository.ApplianceModelProfileRepository;
import com.wattsmart.backend.homes.repository.ApplianceTypeRepository;
import com.wattsmart.backend.homes.repository.TariffPlanRepository;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeConfigurationService {

    private final TariffPlanRepository tariffPlanRepository;
    private final ApplianceTypeRepository applianceTypeRepository;
    private final ApplianceModelProfileRepository applianceModelProfileRepository;

    @Transactional(readOnly = true)
    public RegistrationOptionsResponse getRegistrationOptions() {
        var tariffPlans = tariffPlanRepository.findAll().stream()
                .sorted(Comparator.comparing(t -> t.getName().toLowerCase()))
                .map(tariffPlan -> new TariffPlanOption(
                        tariffPlan.getId(),
                        tariffPlan.getCode(),
                        tariffPlan.getName(),
                        tariffPlan.getDescription(),
                        tariffPlan.getCurrencyCode(),
                        tariffPlan.getBaseRatePerKwh(),
                        tariffPlan.isActive()))
                .toList();

        var applianceTypes = applianceTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(t -> t.getDisplayName().toLowerCase()))
                .map(applianceType -> new ApplianceTypeOption(
                        applianceType.getId(),
                        applianceType.getCode(),
                        applianceType.getDisplayName(),
                        applianceType.getDescription(),
                        applianceType.getTypicalWatts(),
                        applianceType.getDefaultSafeWattLimit(),
                        applianceType.getPeakWattLimit()))
                .toList();

        var applianceModelProfiles = applianceModelProfileRepository.findAllWithApplianceType().stream()
                .map(profile -> new ApplianceModelProfileOption(
                        profile.getId(),
                        profile.getApplianceType().getId(),
                        profile.getApplianceType().getCode(),
                        profile.getApplianceType().getDisplayName(),
                        profile.getManufacturer(),
                        profile.getModelName(),
                        profile.getDisplayName(),
                        profile.getNominalWattage(),
                        profile.getSafeWattLimit(),
                        profile.getPeakWattLimit(),
                        profile.getSourceName()))
                .toList();

        return new RegistrationOptionsResponse(tariffPlans, applianceTypes, applianceModelProfiles);
    }
}
