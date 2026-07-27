import { Grid2X2, LayoutList, RefreshCw } from "lucide-react";
import type { HomeStatusItem } from "../types";
import { HomeCard } from "./HomeCard";

type Props = {
  homes: HomeStatusItem[];
  isMobile?: boolean;
  loading: boolean;
  operatorMode: boolean;
  viewMode: "list" | "grid";
  onRefresh: () => void;
  onSelectHome: (home: HomeStatusItem) => void;
  onSetViewMode: (mode: "list" | "grid") => void;
};

export function HomeMonitoringPanel(props: Props) {
  const activeAnomalies = props.homes.flatMap((home) => home.appliances).filter((appliance) => appliance.anomalyActive).length;
  const penaltyHomes = props.homes.filter((home) => home.billing.highestMilestoneStage === "PENALTY").length;
  const resolvedViewMode = props.isMobile ? "list" : props.viewMode;

  return (
    <section className="monitoring-panel">
      <div className="panel-toolbar">
        <div className="toolbar-actions">
          {!props.isMobile && (
            <div className="view-toggle" aria-label="Toggle house view">
              <button className={props.viewMode === "grid" ? "active" : ""} onClick={() => props.onSetViewMode("grid")}>
                <Grid2X2 size={16} /> Grid
              </button>
              <button className={props.viewMode === "list" ? "active" : ""} onClick={() => props.onSetViewMode("list")}>
                <LayoutList size={16} /> List
              </button>
            </div>
          )}
          <button className="ghost-button" disabled={props.loading} onClick={props.onRefresh}>
            <RefreshCw size={16} /> Refresh
          </button>
        </div>
        <div className="toolbar-stats">
          <ToolbarStat label="Penalty homes" value={penaltyHomes.toString()} />
          <ToolbarStat label="Active anomalies" value={activeAnomalies.toString()} />
        </div>
      </div>

      <div className={`home-list ${resolvedViewMode}`}>
        {props.homes.map((home) => (
          <HomeCard key={home.homeId} home={home} onOpen={() => props.onSelectHome(home)} />
        ))}
        {props.homes.length === 0 && (
          <div className="empty-state">
            No homes are visible yet.
          </div>
        )}
      </div>
    </section>
  );
}

function ToolbarStat({ label, value }: { label: string; value: string }) {
  return (
    <span className="toolbar-stat">
      <span>{label}</span>
      <strong>{value}</strong>
    </span>
  );
}
