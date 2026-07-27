package com.wattsmart.backend.telemetry.live;

import com.wattsmart.backend.homes.domain.HomeStatus;
import com.wattsmart.backend.homes.domain.MilestoneStage;
import com.wattsmart.backend.homes.domain.UsagePercentageMilestone;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LiveHomeState implements Serializable {

    private UUID homeId;
    private String homeExternalKey;
    private String homeName;
    private HomeStatus homeStatus;
    private String timezoneName;
    private String currentCycleStartedOn;
    private String currentCycleEndsOn;
    private String lastCapturedAt;
    private BigDecimal totalInstantWatts;
    private BigDecimal currentCycleUsageKwh;
    private BigDecimal currentCycleBaseCostAmount;
    private BigDecimal currentCyclePenaltyCostAmount;
    private BigDecimal totalCostAmount;
    private UsagePercentageMilestone highestMilestoneReached;
    private MilestoneStage highestMilestoneStage;
    private Map<UUID, LiveApplianceState> appliances = new HashMap<>();

    public LiveHomeState() {
    }

    public LiveHomeState(
            UUID homeId,
            String homeExternalKey,
            String homeName,
            HomeStatus homeStatus,
            String timezoneName,
            String currentCycleStartedOn,
            String currentCycleEndsOn,
            String lastCapturedAt,
            BigDecimal totalInstantWatts,
            BigDecimal currentCycleUsageKwh,
            BigDecimal currentCycleBaseCostAmount,
            BigDecimal currentCyclePenaltyCostAmount,
            BigDecimal totalCostAmount,
            UsagePercentageMilestone highestMilestoneReached,
            MilestoneStage highestMilestoneStage,
            Map<UUID, LiveApplianceState> appliances
    ) {
        this.homeId = homeId;
        this.homeExternalKey = homeExternalKey;
        this.homeName = homeName;
        this.homeStatus = homeStatus;
        this.timezoneName = timezoneName;
        this.currentCycleStartedOn = currentCycleStartedOn;
        this.currentCycleEndsOn = currentCycleEndsOn;
        this.lastCapturedAt = lastCapturedAt;
        this.totalInstantWatts = totalInstantWatts;
        this.currentCycleUsageKwh = currentCycleUsageKwh;
        this.currentCycleBaseCostAmount = currentCycleBaseCostAmount;
        this.currentCyclePenaltyCostAmount = currentCyclePenaltyCostAmount;
        this.totalCostAmount = totalCostAmount;
        this.highestMilestoneReached = highestMilestoneReached;
        this.highestMilestoneStage = highestMilestoneStage;
        this.appliances = appliances != null ? new HashMap<>(appliances) : new HashMap<>();
    }

    public UUID homeId() {
        return homeId;
    }

    public String homeExternalKey() {
        return homeExternalKey;
    }

    public String homeName() {
        return homeName;
    }

    public HomeStatus homeStatus() {
        return homeStatus;
    }

    public String timezoneName() {
        return timezoneName;
    }

    public String currentCycleStartedOn() {
        return currentCycleStartedOn;
    }

    public String currentCycleEndsOn() {
        return currentCycleEndsOn;
    }

    public String lastCapturedAt() {
        return lastCapturedAt;
    }

    public BigDecimal totalInstantWatts() {
        return totalInstantWatts;
    }

    public BigDecimal currentCycleUsageKwh() {
        return currentCycleUsageKwh;
    }

    public BigDecimal currentCycleBaseCostAmount() {
        return currentCycleBaseCostAmount;
    }

    public BigDecimal currentCyclePenaltyCostAmount() {
        return currentCyclePenaltyCostAmount;
    }

    public BigDecimal totalCostAmount() {
        return totalCostAmount;
    }

    public UsagePercentageMilestone highestMilestoneReached() {
        return highestMilestoneReached;
    }

    public MilestoneStage highestMilestoneStage() {
        return highestMilestoneStage;
    }

    public Map<UUID, LiveApplianceState> appliances() {
        return appliances;
    }
}
