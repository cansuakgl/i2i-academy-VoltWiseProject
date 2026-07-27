package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import com.wattsmart.backend.homes.api.dto.HomeRegistrationRequest;
import com.wattsmart.backend.homes.api.dto.HomeRegistrationResponse;
import com.wattsmart.backend.homes.domain.Appliance;
import com.wattsmart.backend.homes.domain.ApplianceType;
import com.wattsmart.backend.homes.domain.Home;
import com.wattsmart.backend.homes.domain.HomeBillingAccount;
import com.wattsmart.backend.homes.domain.HomeTariffPlan;
import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.domain.HomeUserMembership;
import com.wattsmart.backend.homes.domain.TariffPlan;
import com.wattsmart.backend.homes.events.HomeRegistrationEvent;
import com.wattsmart.backend.homes.events.HomeRegistrationEvent.RegisteredAppliance;
import com.wattsmart.backend.homes.events.HomeRegistrationEventPublisher;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.homes.repository.ApplianceTypeRepository;
import com.wattsmart.backend.homes.repository.HomeBillingAccountRepository;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.homes.repository.HomeTariffPlanRepository;
import com.wattsmart.backend.homes.repository.HomeUserMembershipRepository;
import com.wattsmart.backend.homes.repository.TariffPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeRegistrationService {

    private static final short DEFAULT_BILLING_CYCLE_START_DAY = 1;

    private final HomeRepository homeRepository;
    private final TariffPlanRepository tariffPlanRepository;
    private final ApplianceTypeRepository applianceTypeRepository;
    private final HomeTariffPlanRepository homeTariffPlanRepository;
    private final HomeBillingAccountRepository homeBillingAccountRepository;
    private final ApplianceRepository applianceRepository;
    private final HomeUserMembershipRepository homeUserMembershipRepository;
    private final HomeRegistrationEventPublisher eventPublisher;
    private final HomeLiveStateSyncService homeLiveStateSyncService;

    @Transactional
    public HomeRegistrationResponse register(HomeRegistrationRequest request, AppUser user) {
        validateRequest(request);

        TariffPlan tariffPlan = resolveTariffPlan(request);
        Map<String, ApplianceType> applianceTypes = resolveApplianceTypes(request);

        Home home = buildHome(request);
        homeRepository.save(home);

        HomeTariffPlan homeTariffPlan = buildHomeTariffPlan(request, home, tariffPlan);
        homeTariffPlanRepository.save(homeTariffPlan);

        HomeBillingAccount billingAccount = buildBillingAccount(home, homeTariffPlan);
        homeBillingAccountRepository.save(billingAccount);

        List<Appliance> appliances = appliancesOrEmpty(request).stream()
                .map(item -> buildAppliance(home, item, applianceTypes.get(item.typeCode())))
                .toList();
        List<Appliance> savedAppliances = applianceRepository.saveAll(appliances);

        HomeUserMembership membership = createMembership(home, user);
        homeLiveStateSyncService.syncHome(home, billingAccount, savedAppliances);

        eventPublisher.publish(buildEvent(home, tariffPlan, savedAppliances, request));

        return new HomeRegistrationResponse(
                home.getId(),
                home.getExternalKey(),
                home.getName(),
                tariffPlan.getId(),
                savedAppliances.size(),
                savedAppliances.stream().map(Appliance::getId).toList(),
                membership != null ? membership.getId() : null
        );
    }

    private void validateRequest(HomeRegistrationRequest request) {
        if (homeRepository.existsByExternalKey(request.externalKey())) {
            throw new BadRequestException("A home with external key '" + request.externalKey() + "' already exists.");
        }

        Set<String> applianceCodes = new HashSet<>();
        for (HomeRegistrationRequest.ApplianceRegistrationRequest appliance : appliancesOrEmpty(request)) {
            if (!applianceCodes.add(appliance.applianceCode())) {
                throw new BadRequestException("Duplicate appliance code in request: " + appliance.applianceCode());
            }
        }
    }

    private TariffPlan resolveTariffPlan(HomeRegistrationRequest request) {
        return tariffPlanRepository.findById(request.billing().tariffPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Tariff plan not found: " + request.billing().tariffPlanId()));
    }

    private Map<String, ApplianceType> resolveApplianceTypes(HomeRegistrationRequest request) {
        List<String> codes = appliancesOrEmpty(request).stream()
                .map(HomeRegistrationRequest.ApplianceRegistrationRequest::typeCode)
                .distinct()
                .toList();

        Map<String, ApplianceType> applianceTypesByCode = new HashMap<>();
        applianceTypeRepository.findByCodeIn(codes)
                .forEach(type -> applianceTypesByCode.put(type.getCode(), type));

        List<String> missingCodes = codes.stream()
                .filter(code -> !applianceTypesByCode.containsKey(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new ResourceNotFoundException("Unknown appliance type codes: " + String.join(", ", missingCodes));
        }
        return applianceTypesByCode;
    }

    private Home buildHome(HomeRegistrationRequest request) {
        Home home = new Home();
        home.setExternalKey(resolveExternalKey(request));
        home.setName(request.name());
        home.setStatus(HomeStatus.ACTIVE);
        home.setAddressLine1(request.addressLine1());
        home.setAddressLine2(request.addressLine2());
        home.setCity(request.city());
        home.setRegion(request.region());
        home.setPostalCode(request.postalCode());
        home.setCountryCode(request.countryCode());
        home.setTimezoneName(request.timezoneName());
        return home;
    }

    private HomeTariffPlan buildHomeTariffPlan(
            HomeRegistrationRequest request,
            Home home,
            TariffPlan tariffPlan
    ) {
        HomeTariffPlan homeTariffPlan = new HomeTariffPlan();
        homeTariffPlan.setHome(home);
        homeTariffPlan.setTariffPlan(tariffPlan);
        homeTariffPlan.setMonthlyUsageLimitKwh(request.billing().monthlyUsageLimitKwh());
        homeTariffPlan.setBillingCycleStartDay(request.billing().billingCycleStartDay() != null
                ? request.billing().billingCycleStartDay()
                : DEFAULT_BILLING_CYCLE_START_DAY);
        homeTariffPlan.setEffectiveFrom(LocalDate.now(ZoneId.of(home.getTimezoneName())));
        return homeTariffPlan;
    }

    private HomeBillingAccount buildBillingAccount(Home home, HomeTariffPlan homeTariffPlan) {
        HomeBillingAccount billingAccount = new HomeBillingAccount();
        billingAccount.setHome(home);
        billingAccount.setCurrentCycleStartedOn(resolveCycleStart(home.getTimezoneName(), homeTariffPlan.getBillingCycleStartDay()));
        return billingAccount;
    }

    private Appliance buildAppliance(
            Home home,
            HomeRegistrationRequest.ApplianceRegistrationRequest request,
            ApplianceType applianceType
    ) {
        Appliance appliance = new Appliance();
        appliance.setHome(home);
        appliance.setApplianceType(applianceType);
        appliance.setApplianceCode(request.applianceCode());
        appliance.setName(request.name());
        appliance.setManufacturer(request.manufacturer());
        appliance.setModelName(request.modelName());
        appliance.setNominalWattage(request.nominalWattage());
        appliance.setSafeWattLimit(defaultIfNull(request.safeWattLimit(), applianceType.getDefaultSafeWattLimit()));
        appliance.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : (short) 0);
        appliance.setInstalledAt(request.installedAt());
        appliance.setActive(true);
        return appliance;
    }

    private HomeUserMembership createMembership(Home home, AppUser user) {
        HomeUserMembership membership = new HomeUserMembership();
        membership.setHome(home);
        membership.setUser(user);
        membership.setAcceptedAt(OffsetDateTime.now());
        return homeUserMembershipRepository.save(membership);
    }

    private HomeRegistrationEvent buildEvent(
            Home home,
            TariffPlan tariffPlan,
            List<Appliance> appliances,
            HomeRegistrationRequest request
    ) {
        List<RegisteredAppliance> eventAppliances = appliances.stream()
                .map(appliance -> new RegisteredAppliance(
                        appliance.getId(),
                        appliance.getApplianceCode(),
                        appliance.getName(),
                        appliance.getApplianceType().getCode(),
                        appliance.getApplianceType().getTypicalWatts(),
                        appliance.getSafeWattLimit()))
                .toList();

        return new HomeRegistrationEvent(
                home.getId(),
                home.getExternalKey(),
                home.getName(),
                home.getTimezoneName(),
                tariffPlan.getId(),
                eventAppliances,
                OffsetDateTime.now()
        );
    }

    private LocalDate resolveCycleStart(String timezoneName, short billingCycleStartDay) {
        LocalDate localDate = LocalDate.now(ZoneId.of(timezoneName));
        int resolvedDay = Math.min(billingCycleStartDay, localDate.lengthOfMonth());
        LocalDate cycleStart = localDate.withDayOfMonth(resolvedDay);
        return localDate.getDayOfMonth() >= resolvedDay ? cycleStart : cycleStart.minusMonths(1);
    }

    private BigDecimal defaultIfNull(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private List<HomeRegistrationRequest.ApplianceRegistrationRequest> appliancesOrEmpty(HomeRegistrationRequest request) {
        return request.appliances() != null ? request.appliances() : Collections.emptyList();
    }

    private String resolveExternalKey(HomeRegistrationRequest request) {
        if (request.externalKey() != null && !request.externalKey().isBlank()) {
            return request.externalKey();
        }
        return "HOME-" + UUID.randomUUID();
    }
}
