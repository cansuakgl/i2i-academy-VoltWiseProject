package com.wattsmart.backend.telemetry.simulator;

import com.wattsmart.backend.common.idempotency.IdempotencyService;
import com.wattsmart.backend.homes.domain.Appliance;
import com.wattsmart.backend.homes.events.HomeRegistrationEvent;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.telemetry.events.ApplianceTelemetryEvent;
import com.wattsmart.backend.telemetry.events.ApplianceTelemetryEvent.ApplianceReading;
import com.wattsmart.backend.telemetry.events.ApplianceTelemetryEventPublisher;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.telemetry-simulator.enabled", havingValue = "true")
public class HomeTelemetrySimulator {

    private final ApplianceRepository applianceRepository;
    private final ApplianceTelemetryEventPublisher telemetryEventPublisher;
    private final IdempotencyService idempotencyService;
    private final Map<UUID, SimulatedHome> homes = new ConcurrentHashMap<>();

    @Value("${app.telemetry-simulator.fluctuation-percent}")
    private int fluctuationPercent;

    @Value("${app.telemetry-simulator.anomaly-chance-percent}")
    private int anomalyChancePercent;

    @PostConstruct
    public void loadExistingHomes() {
        Map<UUID, List<SimulatedAppliance>> appliancesByHome = new ConcurrentHashMap<>();
        Map<UUID, String> externalKeysByHome = new ConcurrentHashMap<>();

        for (Appliance appliance : applianceRepository.findActiveAppliancesForSimulation()) {
            externalKeysByHome.put(appliance.getHome().getId(), appliance.getHome().getExternalKey());
            appliancesByHome.computeIfAbsent(appliance.getHome().getId(), ignored -> new ArrayList<>())
                    .add(toSimulatedAppliance(appliance));
        }

        appliancesByHome.forEach((homeId, appliances) ->
                homes.put(homeId, new SimulatedHome(homeId, externalKeysByHome.get(homeId), List.copyOf(appliances))));

        log.info("Loaded {} homes into telemetry simulator.", homes.size());
    }

    @KafkaListener(
            topics = "${app.kafka.topics.home-registration}",
            groupId = "${spring.kafka.consumer.group-id}-telemetry-simulator",
            autoStartup = "${app.kafka.listener.auto-startup}"
    )
    public void handleHomeRegistration(HomeRegistrationEvent event) {
        try {
            String idempotencyKey = "home-registration:" + event.homeId() + ":" + event.registeredAt();
            if (!idempotencyService.tryClaim(idempotencyKey)) {
                log.info("Skipped duplicate home registration event in telemetry simulator. homeId={}", event.homeId());
                return;
            }

            List<SimulatedAppliance> appliances = event.appliances().stream()
                    .map(appliance -> new SimulatedAppliance(
                            appliance.applianceId(),
                            appliance.applianceCode(),
                            appliance.applianceTypeCode(),
                            appliance.typicalWatts(),
                            appliance.safeWattLimit()))
                    .toList();

            homes.put(event.homeId(), new SimulatedHome(event.homeId(), event.externalKey(), appliances));
            log.info("Registered home in telemetry simulator. homeId={}, appliances={}", event.homeId(), appliances.size());
        } catch (RuntimeException exception) {
            log.error("Home registration Kafka consume failed in telemetry simulator. homeId={}",
                    event != null ? event.homeId() : null,
                    exception);
            throw exception;
        }
    }

    @Scheduled(fixedDelayString = "${app.telemetry-simulator.publish-interval-ms}")
    public void publishTelemetry() {
        homes.values().forEach(home -> {
            if (home.appliances().isEmpty()) {
                return;
            }

            List<ApplianceReading> readings = home.appliances().stream()
                    .map(this::generateReading)
                    .toList();

            telemetryEventPublisher.publish(new ApplianceTelemetryEvent(
                    UUID.randomUUID(),
                    home.homeId(),
                    home.externalKey(),
                    OffsetDateTime.now(),
                    readings));
        });
    }

    private SimulatedAppliance toSimulatedAppliance(Appliance appliance) {
        BigDecimal safeWattLimit = appliance.getSafeWattLimit() != null
                ? appliance.getSafeWattLimit()
                : appliance.getApplianceType().getDefaultSafeWattLimit();

        BigDecimal averageWatts = appliance.getNominalWattage() != null
                ? appliance.getNominalWattage()
                : appliance.getApplianceType().getTypicalWatts();

        return new SimulatedAppliance(
                appliance.getId(),
                appliance.getApplianceCode(),
                appliance.getApplianceType().getCode(),
                averageWatts,
                safeWattLimit);
    }

    private ApplianceReading generateReading(SimulatedAppliance appliance) {
        BigDecimal wattage = calculateWattage(appliance);
        return new ApplianceReading(
                appliance.applianceId(),
                appliance.applianceCode(),
                appliance.applianceTypeCode(),
                wattage,
                appliance.safeWattLimit(),
                wattage.compareTo(appliance.safeWattLimit()) > 0);
    }

    private BigDecimal calculateWattage(SimulatedAppliance appliance) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean anomalous = random.nextInt(100) < anomalyChancePercent;

        if (anomalous) {
            double multiplier = random.nextDouble(1.05, 1.35);
            return appliance.safeWattLimit()
                    .multiply(BigDecimal.valueOf(multiplier))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        double minMultiplier = 1 - (fluctuationPercent / 100.0);
        double maxMultiplier = 1 + (fluctuationPercent / 100.0);
        return appliance.averageWatts()
                .multiply(BigDecimal.valueOf(random.nextDouble(minMultiplier, maxMultiplier)))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
