package com.wattsmart.backend.telemetry.live;

import java.io.Serializable;
import java.math.BigDecimal;

public class LiveApplianceUsageWindow implements Serializable {

    private String windowStartedAt;
    private String windowEndedAt;
    private BigDecimal energyKwh;
    private BigDecimal wattSeconds;
    private BigDecimal peakWatts;
    private int sampleCount;

    public LiveApplianceUsageWindow() {
    }

    public LiveApplianceUsageWindow(
            String windowStartedAt,
            String windowEndedAt,
            BigDecimal energyKwh,
            BigDecimal wattSeconds,
            BigDecimal peakWatts,
            int sampleCount
    ) {
        this.windowStartedAt = windowStartedAt;
        this.windowEndedAt = windowEndedAt;
        this.energyKwh = energyKwh;
        this.wattSeconds = wattSeconds;
        this.peakWatts = peakWatts;
        this.sampleCount = sampleCount;
    }

    public String windowStartedAt() {
        return windowStartedAt;
    }

    public String windowEndedAt() {
        return windowEndedAt;
    }

    public BigDecimal energyKwh() {
        return energyKwh;
    }

    public BigDecimal wattSeconds() {
        return wattSeconds;
    }

    public BigDecimal peakWatts() {
        return peakWatts;
    }

    public int sampleCount() {
        return sampleCount;
    }
}
