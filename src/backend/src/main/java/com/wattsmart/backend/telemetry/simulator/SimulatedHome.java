package com.wattsmart.backend.telemetry.simulator;

import java.util.List;
import java.util.UUID;

record SimulatedHome(
        UUID homeId,
        String externalKey,
        List<SimulatedAppliance> appliances
) {
}
