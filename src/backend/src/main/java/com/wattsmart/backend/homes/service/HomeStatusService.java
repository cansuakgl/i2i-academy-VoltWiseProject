git stspackage com.wattsmart.backend.homes.service;

import com.wattsmart.backend.homes.api.dto.HomeStatusResponse;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse.ApplianceStatusItem;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse.BillingStatus;
import com.wattsmart.backend.homes.api.dto.HomeStatusResponse.HomeStatusItem;
import com.wattsmart.backend.homes.domain.Appliance;
import com.wattsmart.backend.homes.domain.ApplianceTypeProfile;
import com.wattsmart.backend.homes.domain.Home;
import com.wattsmart.backend.homes.domain.HomeBillingAccount;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.homes.repository.HomeBillingAccountRepository;
import com.wattsmart.backend.homes.repository.HomeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeStatusService {

    private final HomeRepository homeRepository;
    private final HomeBillingAccountRepository homeBillingAccountRepository;
    private final ApplianceRepository applianceRepository;

    @Transactional(readOnly = true)
    public HomeStatusResponse getDashboardStatus() {
        List<Home> homes = homeRepository.findAllByOrderByNameAsc();
        List<UUID> homeIds = homes.stream()
                .map(Home::getId)
                .toList();

        Map<UUID, HomeBillingAccount> billingByHomeId = homeBillingAccountRepository.findByHomeIdIn(homeIds)
                .stream()
                .collect(Collectors.toMap(account -> account.getHome().getId(), account -> account));

        Map<UUID, List<Appliance>> appliancesByHomeId = applianceRepository.findStatusAppliancesByHomeIds(homeIds)
                .stream()
                .collect(Collectors.groupingBy(appliance -> appliance.getHome().getId()));

        List<HomeStatusItem> items = homes.stream()
                .map(home -> toStatusItem(home, billingByHomeId.get(home.getId()), appliancesByHomeId.getOrDefault(home.getId(), List.of())))
                .toList();

        return new HomeStatusResponse(items);
    }

    private HomeStatusItem toStatusItem(Home home, HomeBillingAccount billingAccount, List<Appliance> appliances) {
        return new HomeStatusItem(
                home.getId(),
                home.getExternalKey(),
                home.getName(),
                home.getContactEmail(),
                home.getStatus(),
                home.getTimezoneName(),
                toBillingStatus(billingAccount),
                appliances.stream().map(this::toApplianceStatus).toList()
        );
    }

    private BillingStatus toBillingStatus(HomeBillingAccount account) {
        if (account == null) {
            return null;
        }

        return new BillingStatus(
                account.getCurrentCycleStartedOn(),
                account.getCurrentCycleEnergyKwh(),
                account.getCurrentCycleBaseCostAmount(),
                account.getCurrentCyclePenaltyCostAmount(),
                account.getTotalCostAmount(),
                account.getQuotaState(),
                account.isPenaltyActive(),
                account.getLastTelemetryReceivedAt(),
                account.getLastRollupAt()
        );
    }

    private ApplianceStatusItem toApplianceStatus(Appliance appliance) {
        ApplianceTypeProfile profile = appliance.getApplianceTypeProfile();
        return new ApplianceStatusItem(
                appliance.getId(),
                appliance.getApplianceCode(),
                appliance.getName(),
                profile.getCode(),
                profile.getDisplayName(),
                defaultIfNull(appliance.getNominalWattage(), profile.getAverageWatts()),
                defaultIfNull(appliance.getSafeWattLimit(), profile.getDefaultSafeWattLimit()),
                defaultIfNull(appliance.getAllowedDeviationPct(), profile.getAllowedDeviationPct()),
                appliance.getAnomalyCycleThreshold(),
                appliance.isActive()
        );
    }

    private BigDecimal defaultIfNull(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }
}
