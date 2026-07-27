package com.wattsmart.backend.telemetry.live;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public class LiveApplianceState implements Serializable {

    private UUID applianceId;
    private String applianceCode;
    private String applianceName;
    private String applianceTypeCode;
    private String applianceTypeDisplayName;
    private BigDecimal typicalWatts;
    private BigDecimal latestWattage;
    private BigDecimal safeWattLimit;
    private boolean aboveSafeLimit;
    private int consecutiveBreachCount;
    private String breachStartedAt;
    private boolean anomalyActive;
    private String lastCapturedAt;
    private LiveApplianceUsageWindow currentUsageWindow;
    private LiveApplianceUsageWindow completedUsageWindow;
    private boolean active;

    public LiveApplianceState() {
    }

    public LiveApplianceState(
            UUID applianceId,
            String applianceCode,
            String applianceName,
            String applianceTypeCode,
            String applianceTypeDisplayName,
            BigDecimal typicalWatts,
            BigDecimal latestWattage,
            BigDecimal safeWattLimit,
            boolean aboveSafeLimit,
            int consecutiveBreachCount,
            String breachStartedAt,
            boolean anomalyActive,
            String lastCapturedAt,
            LiveApplianceUsageWindow currentUsageWindow,
            LiveApplianceUsageWindow completedUsageWindow,
            boolean active
    ) {
        this.applianceId = applianceId;
        this.applianceCode = applianceCode;
        this.applianceName = applianceName;
        this.applianceTypeCode = applianceTypeCode;
        this.applianceTypeDisplayName = applianceTypeDisplayName;
        this.typicalWatts = typicalWatts;
        this.latestWattage = latestWattage;
        this.safeWattLimit = safeWattLimit;
        this.aboveSafeLimit = aboveSafeLimit;
        this.consecutiveBreachCount = consecutiveBreachCount;
        this.breachStartedAt = breachStartedAt;
        this.anomalyActive = anomalyActive;
        this.lastCapturedAt = lastCapturedAt;
        this.currentUsageWindow = currentUsageWindow;
        this.completedUsageWindow = completedUsageWindow;
        this.active = active;
    }

    public UUID applianceId() {
        return applianceId;
    }

    public String applianceCode() {
        return applianceCode;
    }

    public String applianceName() {
        return applianceName;
    }

    public String applianceTypeCode() {
        return applianceTypeCode;
    }

    public String applianceTypeDisplayName() {
        return applianceTypeDisplayName;
    }

    public BigDecimal typicalWatts() {
        return typicalWatts;
    }

    public BigDecimal latestWattage() {
        return latestWattage;
    }

    public BigDecimal safeWattLimit() {
        return safeWattLimit;
    }

    public boolean aboveSafeLimit() {
        return aboveSafeLimit;
    }

    public int consecutiveBreachCount() {
        return consecutiveBreachCount;
    }

    public String breachStartedAt() {
        return breachStartedAt;
    }

    public boolean anomalyActive() {
        return anomalyActive;
    }

    public String lastCapturedAt() {
        return lastCapturedAt;
    }

    public LiveApplianceUsageWindow currentUsageWindow() {
        return currentUsageWindow;
    }

    public LiveApplianceUsageWindow completedUsageWindow() {
        return completedUsageWindow;
    }

    public boolean active() {
        return active;
    }
}
