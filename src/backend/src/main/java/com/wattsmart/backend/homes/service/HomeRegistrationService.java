package com.wattsmart.backend.homes.service;

import com.wattsmart.backend.auth.domain.AppUser;
import com.wattsmart.backend.auth.repository.AppUserRepository;
import com.wattsmart.backend.common.service.BadRequestException;
import com.wattsmart.backend.common.service.ResourceNotFoundException;
import com.wattsmart.backend.homes.api.dto.HomeRegistrationRequest;
import com.wattsmart.backend.homes.api.dto.HomeRegistrationResponse;
import com.wattsmart.backend.homes.domain.Appliance;
import com.wattsmart.backend.homes.domain.ApplianceTypeProfile;
import com.wattsmart.backend.homes.domain.Home;
import com.wattsmart.backend.homes.domain.HomeBillingAccount;
import com.wattsmart.backend.homes.domain.HomeBillingConfig;
import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.domain.HomeUserMembership;
import com.wattsmart.backend.homes.domain.MembershipRole;
import com.wattsmart.backend.homes.domain.TariffPlan;
import com.wattsmart.backend.homes.events.HomeRegistrationEvent;
import com.wattsmart.backend.homes.events.HomeRegistrationEvent.RegisteredAppliance;
import com.wattsmart.backend.homes.events.HomeRegistrationEventPublisher;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.homes.repository.ApplianceTypeProfileRepository;
import com.wattsmart.backend.homes.repository.HomeBillingAccountRepository;
import com.wattsmart.backend.homes.repository.HomeBillingConfigRepository;
import com.wattsmart.backend.homes.repository.HomeRepository;
import com.wattsmart.backend.homes.repository.HomeUserMembershipRepository;
import com.wattsmart.backend.homes.repository.TariffPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    private static final BigDecimal DEFAULT_WARNING_THRESHOLD = new BigDecimal("80.00");
    private static final BigDecimal DEFAULT_CRITICAL_THRESHOLD = new BigDecimal("100.00");
    private static final short DEFAULT_BILLING_CYCLE_START_DAY = 1;

    private final HomeRepository homeRepository;
    private final TariffPlanRepository tariffPlanRepository;
    private final ApplianceTypeProfileRepository applianceTypeProfileRepository;
    private final HomeBillingConfigRepository homeBillingConfigRepository;
    private final HomeBillingAccountRepository homeBillingAccountRepository;
    private final ApplianceRepository applianceRepository;
    private final HomeUserMembershipRepository homeUserMembershipRepository;
    private final AppUserRepository appUserRepository;
    private final HomeRegistrationEventPublisher eventPublisher;

    @Transactional
    public HomeRegistrationResponse register(HomeRegistrationRequest request) {
        validateRequest(request);

        TariffPlan tariffPlan = resolveTariffPlan(request);
        Map<String, ApplianceTypeProfile> typeProfiles = resolveTypeProfiles(request);

        Home home = buildHome(request);
        homeRepository.save(home);

        HomeBillingConfig billingConfig = buildBillingConfig(request, home, tariffPlan);
        homeBillingConfigRepository.save(billingConfig);

        HomeBillingAccount billingAccount = buildBillingAccount(home, billingConfig);
        homeBillingAccountRepository.save(billingAccount);

        List<Appliance> appliances = request.appliances().stream()
                .map(item -> buildAppliance(home, item, typeProfiles.get(item.typeProfileCode())))
                .toList();
        applianceRepository.saveAll(appliances);

        HomeUserMembership membership = createOwnerMembershipIfPresent(home, request);

        eventPublisher.publish(buildEvent(home, tariffPlan, appliances, request));

        return new HomeRegistrationResponse(
                home.getId(),
                home.getExternalKey(),
                home.getName(),
                tariffPlan.getId(),
                appliances.size(),
                appliances.stream().map(Appliance::getId).toList(),
                membership != null ? membership.getId() : null
        );
    }

    private void validateRequest(HomeRegistrationRequest request) {
        if (homeRepository.existsByExternalKey(request.externalKey())) {
            throw new BadRequestException("A home with external key '" + request.externalKey() + "' already exists.");
        }

        Set<String> applianceCodes = new HashSet<>();
        for (HomeRegistrationRequest.ApplianceRegistrationRequest appliance : request.appliances()) {
            if (!applianceCodes.add(appliance.applianceCode())) {
                throw new BadRequestException("Duplicate appliance code in request: " + appliance.applianceCode());
            }
        }
    }

    private TariffPlan resolveTariffPlan(HomeRegistrationRequest request) {
        UUID tariffPlanId = request.billing().tariffPlanId();
        if (tariffPlanId != null) {
            return tariffPlanRepository.findById(tariffPlanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tariff plan not found: " + tariffPlanId));
        }

        return tariffPlanRepository.findByDefaultPlanTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No default tariff plan is configured."));
    }

    private Map<String, ApplianceTypeProfile> resolveTypeProfiles(HomeRegistrationRequest request) {
        List<String> codes = request.appliances().stream()
                .map(HomeRegistrationRequest.ApplianceRegistrationRequest::typeProfileCode)
                .distinct()
                .toList();

        Map<String, ApplianceTypeProfile> profilesByCode = new HashMap<>();
        applianceTypeProfileRepository.findByCodeIn(codes)
                .forEach(profile -> profilesByCode.put(profile.getCode(), profile));

        List<String> missingCodes = codes.stream()
                .filter(code -> !profilesByCode.containsKey(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new ResourceNotFoundException("Unknown appliance type profile codes: " + String.join(", ", missingCodes));
        }
        return profilesByCode;
    }

    private Home buildHome(HomeRegistrationRequest request) {
        Home home = new Home();
        home.setExternalKey(request.externalKey());
        home.setName(request.name());
        home.setContactEmail(request.contactEmail());
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

    private HomeBillingConfig buildBillingConfig(
            HomeRegistrationRequest request,
            Home home,
            TariffPlan tariffPlan
    ) {
        HomeBillingConfig billingConfig = new HomeBillingConfig();
        billingConfig.setHome(home);
        billingConfig.setTariffPlan(tariffPlan);
        billingConfig.setMonthlyBudgetAmount(request.billing().monthlyBudgetAmount());
        billingConfig.setMonthlyEnergyQuotaKwh(request.billing().monthlyEnergyQuotaKwh());
        billingConfig.setQuotaWarningThresholdPct(defaultIfNull(
                request.billing().quotaWarningThresholdPct(),
                DEFAULT_WARNING_THRESHOLD
        ));
        billingConfig.setQuotaCriticalThresholdPct(defaultIfNull(
                request.billing().quotaCriticalThresholdPct(),
                DEFAULT_CRITICAL_THRESHOLD
        ));
        billingConfig.setBillingCycleStartDay(request.billing().billingCycleStartDay() != null
                ? request.billing().billingCycleStartDay()
                : DEFAULT_BILLING_CYCLE_START_DAY);
        return billingConfig;
    }

    private HomeBillingAccount buildBillingAccount(Home home, HomeBillingConfig billingConfig) {
        HomeBillingAccount billingAccount = new HomeBillingAccount();
        billingAccount.setHome(home);
        billingAccount.setCurrentCycleStartedOn(resolveCycleStart(home.getTimezoneName(), billingConfig.getBillingCycleStartDay()));
        return billingAccount;
    }

    private Appliance buildAppliance(
            Home home,
            HomeRegistrationRequest.ApplianceRegistrationRequest request,
            ApplianceTypeProfile profile
    ) {
        Appliance appliance = new Appliance();
        appliance.setHome(home);
        appliance.setApplianceTypeProfile(profile);
        appliance.setApplianceCode(request.applianceCode());
        appliance.setName(request.name());
        appliance.setManufacturer(request.manufacturer());
        appliance.setModelNumber(request.modelNumber());
        appliance.setNominalWattage(request.nominalWattage());
        appliance.setSafeWattLimit(defaultIfNull(request.safeWattLimit(), profile.getDefaultSafeWattLimit()));
        appliance.setAllowedDeviationPct(defaultIfNull(request.allowedDeviationPct(), profile.getAllowedDeviationPct()));
        appliance.setAnomalyCycleThreshold(request.anomalyCycleThreshold() != null
                ? request.anomalyCycleThreshold()
                : profile.getDefaultAnomalyCycleThreshold());
        appliance.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : (short) 0);
        appliance.setInstalledAt(request.installedAt());
        appliance.setActive(true);
        return appliance;
    }

    private HomeUserMembership createOwnerMembershipIfPresent(Home home, HomeRegistrationRequest request) {
        if (request.ownerUserId() == null) {
            return null;
        }

        AppUser user = appUserRepository.findById(request.ownerUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.ownerUserId()));

        HomeUserMembership membership = new HomeUserMembership();
        membership.setHome(home);
        membership.setUser(user);
        membership.setMembershipRole(request.ownerMembershipRole() != null
                ? request.ownerMembershipRole()
                : MembershipRole.OWNER);
        membership.setPrimary(request.primaryOwner() == null || request.primaryOwner());
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
                        appliance.getApplianceTypeProfile().getCode(),
                        appliance.getApplianceTypeProfile().getAverageWatts(),
                        appliance.getSafeWattLimit()))
                .toList();

        return new HomeRegistrationEvent(
                home.getId(),
                home.getExternalKey(),
                home.getName(),
                request.contactEmail(),
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
}
