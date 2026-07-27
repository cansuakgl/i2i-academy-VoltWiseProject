import { ArrowUpRight, BatteryCharging } from "lucide-react";
import type { HomeStatusItem } from "../types";
import { formatKwh, formatMoney, formatWatts } from "../utils/format";

const houseImageUrl = new URL("../assets/house.svg", import.meta.url).href;

export function HomeCard({ home, onOpen }: { home: HomeStatusItem; onOpen: () => void }) {
  const anomalyCount = home.appliances.filter((appliance) => appliance.anomalyActive || appliance.aboveSafeLimit).length;
  const stage = anomalyCount > 0 ? "penalty" : home.billing.highestMilestoneStage?.toLowerCase() ?? "normal";

  return (
    <article className={`house-card ${stage}`}>
      <img className="house-card-art" src={houseImageUrl} alt="" />
      <div className="house-card-content">
        <div className="house-card-header">
          <div>
            <span className={`status-dot ${stage}`} />
            <h3>{home.name}</h3>
            <small>{home.externalKey}</small>
          </div>
          <button className="open-card-button" onClick={onOpen} aria-label={`Open details for ${home.name}`}>
            <ArrowUpRight size={17} />
          </button>
        </div>

        <div className="house-card-stats">
          <span><BatteryCharging size={15} /> {formatWatts(home.billing.currentTotalWatts)}</span>
          <span>{formatKwh(home.billing.currentCycleUsageKwh)} this cycle</span>
          <span>{formatMoney(home.billing.totalCostAmount)}</span>
          <span>{anomalyCount} anomalies</span>
        </div>
      </div>
    </article>
  );
}
