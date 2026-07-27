package com.wattsmart.backend.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.repository.ApplianceRepository;
import com.wattsmart.backend.integration.IntegrationTestSupport;
import com.wattsmart.backend.telemetry.live.LiveApplianceState;
import com.wattsmart.backend.telemetry.live.LiveHomeState;
import com.wattsmart.backend.telemetry.live.LiveHomeStateStore;
import com.wattsmart.backend.telemetry.live.LiveStateTimeCodec;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomaly;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyStatus;
import com.wattsmart.backend.telemetry.persistence.domain.ApplianceAnomalyType;
import com.wattsmart.backend.telemetry.persistence.repository.ApplianceAnomalyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TelemetryPersistenceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private TelemetryPersistenceService telemetryPersistenceService;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private ApplianceAnomalyRepository applianceAnomalyRepository;

    @Autowired
    private LiveHomeStateStore liveHomeStateStore;

    @Test
    void persistsAndResolvesAnomalyFromLiveStateBreachDuration() throws Exception {
        UserSession resident = registerAndLogin("anomaly-resident");
        var tariffPlan = createTariffPlan("ANOMALY", new BigDecimal("1.10"));
        var applianceType = createApplianceType("ANOMALY-HEATER");
        RegisteredHome home = registerHome(resident.token(), tariffPlan, applianceType, new BigDecimal("300.000"));
        UUID applianceId = applianceRepository.findStatusAppliancesByHomeIds(List.of(home.homeId())).getFirst().getId();

        OffsetDateTime startedAt = OffsetDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime detectedAt = startedAt.plusSeconds(90);
        liveHomeStateStore.saveHomeState(homeState(
                home.homeId(),
                home.externalKey(),
                applianceId,
                true,
                true,
                18,
                startedAt,
                detectedAt,
                new BigDecimal("650.00")));

        telemetryPersistenceService.persistLiveEvents();

        ApplianceAnomaly openAnomaly = applianceAnomalyRepository.findByHomeIdOrderByStartedAtAsc(home.homeId()).getFirst();
        assertThat(openAnomaly.getAnomalyType()).isEqualTo(ApplianceAnomalyType.SAFE_LIMIT_BREACH);
        assertThat(openAnomaly.getStatus()).isEqualTo(ApplianceAnomalyStatus.OPEN);
        assertThat(openAnomaly.getStartedAt()).isEqualTo(startedAt);
        assertThat(openAnomaly.getPeakWatts()).isEqualByComparingTo("650.00");
        assertThat(openAnomaly.getConsecutiveBreachCount()).isEqualTo(18);

        OffsetDateTime resolvedAt = startedAt.plusSeconds(150);
        liveHomeStateStore.saveHomeState(homeState(
                home.homeId(),
                home.externalKey(),
                applianceId,
                false,
                false,
                0,
                null,
                resolvedAt,
                new BigDecimal("120.00")));

        telemetryPersistenceService.persistLiveEvents();

        ApplianceAnomaly resolvedAnomaly = applianceAnomalyRepository.findById(openAnomaly.getId()).orElseThrow();
        assertThat(resolvedAnomaly.getStatus()).isEqualTo(ApplianceAnomalyStatus.RESOLVED);
        assertThat(resolvedAnomaly.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(resolvedAnomaly.getDurationSeconds()).isEqualTo(150);
        assertThat(resolvedAnomaly.getPeakWatts()).isEqualByComparingTo("650.00");
    }

    private LiveHomeState homeState(
            UUID homeId,
            String externalKey,
            UUID applianceId,
            boolean aboveSafeLimit,
            boolean anomalyActive,
            int breachCount,
            OffsetDateTime breachStartedAt,
            OffsetDateTime capturedAt,
            BigDecimal wattage
    ) {
        LiveApplianceState applianceState = new LiveApplianceState(
                applianceId,
                "heater-main",
                "Heater",
                "HEATER",
                "Heater Type",
                new BigDecimal("150.00"),
                wattage,
                new BigDecimal("300.00"),
                aboveSafeLimit,
                breachCount,
                LiveStateTimeCodec.toIso(breachStartedAt),
                anomalyActive,
                LiveStateTimeCodec.toIso(capturedAt),
                null,
                null,
                true);
        return new LiveHomeState(
                homeId,
                externalKey,
                "Integration Home",
                HomeStatus.ACTIVE,
                "Europe/Istanbul",
                LiveStateTimeCodec.toIso(LocalDate.of(2026, 7, 1)),
                null,
                LiveStateTimeCodec.toIso(capturedAt),
                wattage,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                Map.of(applianceId, applianceState));
    }
}
