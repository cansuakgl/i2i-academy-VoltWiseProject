package com.wattsmart.backend.telemetry.simulator;

import java.math.BigDecimal;
import java.util.UUID;

record SimulatedAppliance(
        UUID applianceId,
        String applianceCode,
        String applianceTypeCode,
        BigDecimal averageWatts,
        BigDecimal safeWattLimit
) {
}
