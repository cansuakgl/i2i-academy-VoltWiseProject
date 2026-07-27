package com.wattsmart.backend.jobs;

import com.wattsmart.backend.telemetry.live.LiveHomeState;
import com.wattsmart.backend.telemetry.live.LiveHomeStateStore;
import com.wattsmart.backend.telemetry.live.LiveStateTimeCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillingCycleFinalizationService {

    private static final BigDecimal ZERO_USAGE = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final LiveHomeStateStore liveHomeStateStore;

    public int finalizeDueBillingCycles() {
        List<FinalizedBillingCycle> finalizedCycles = jdbcTemplate.getJdbcOperations().query(
                """
                        SELECT
                            finalized_home_id::TEXT AS home_id,
                            next_cycle_started_on,
                            finalized_timezone_name AS timezone_name
                        FROM wattsmart.finalize_home_billing_cycles()
                        """,
                (resultSet, rowNumber) -> new FinalizedBillingCycle(
                        UUID.fromString(resultSet.getString("home_id")),
                        resultSet.getObject("next_cycle_started_on", LocalDate.class),
                        resultSet.getString("timezone_name")));

        finalizedCycles.forEach(this::resetLiveCycleTotalsIfSafe);
        return finalizedCycles.size();
    }

    private void resetLiveCycleTotalsIfSafe(FinalizedBillingCycle finalizedCycle) {
        LiveHomeState liveState = liveHomeStateStore.getHomeState(finalizedCycle.homeId());
        if (liveState == null) {
            return;
        }
        if (liveState.lastCapturedAt() != null
                && !LiveStateTimeCodec.toOffsetDateTime(liveState.lastCapturedAt()).toInstant().isBefore(finalizedCycle.nextCycleBoundaryInstant())) {
            return;
        }

        liveHomeStateStore.saveHomeState(new LiveHomeState(
                liveState.homeId(),
                liveState.homeExternalKey(),
                liveState.homeName(),
                liveState.homeStatus(),
                liveState.timezoneName(),
                LiveStateTimeCodec.toIso(finalizedCycle.nextCycleStartedOn()),
                null,
                liveState.lastCapturedAt(),
                liveState.totalInstantWatts(),
                ZERO_USAGE,
                ZERO_AMOUNT,
                ZERO_AMOUNT,
                ZERO_AMOUNT,
                null,
                null,
                liveState.appliances()));
    }

    private record FinalizedBillingCycle(
            UUID homeId,
            LocalDate nextCycleStartedOn,
            String timezoneName
    ) {
        private java.time.Instant nextCycleBoundaryInstant() {
            return nextCycleStartedOn.atStartOfDay(ZoneId.of(timezoneName)).toInstant();
        }
    }
}
