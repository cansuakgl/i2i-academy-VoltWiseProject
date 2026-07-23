package com.wattsmart.backend.telemetry.events;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApplianceTelemetryEvent(
        UUID eventId,
        UUID homeId,
        String homeExternalKey,
        OffsetDateTime capturedAt,
        List<ApplianceReading> readings
) {

    public record ApplianceReading(
            UUID applianceId,
            String applianceCode,
            String applianceTypeCode,
            BigDecimal wattage,
            BigDecimal safeWattLimit,
            boolean aboveSafeLimit
    ) {
    }
}
