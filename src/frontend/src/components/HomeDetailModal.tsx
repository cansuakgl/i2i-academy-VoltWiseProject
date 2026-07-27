import { useEffect, useState } from "react";
import { AlertTriangle, BatteryCharging, PlugZap, X } from "lucide-react";
import { api, ApiError } from "../api";
import type {
  AnomalyHistoryResponse,
  BillingCycleHistoryResponse,
  DailyUsageHistoryResponse,
  HomeStatusItem,
  MilestoneHistoryResponse,
  MonthlyUsageHistoryResponse
} from "../types";
import { formatKwh, formatMoney, formatWatts } from "../utils/format";

type Props = {
  token: string;
  home: HomeStatusItem;
  operatorMode: boolean;
  onClose: () => void;
  onMessage: (message: string) => void;
};

export function HomeDetailModal(props: Props) {
  const [history, setHistory] = useState<HomeDetailHistory | null>(null);
  const fromDate = new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);
  const toDate = new Date().toISOString().slice(0, 10);

  useEffect(() => {
    Promise.all(
      [
        api.getHistory<DailyUsageHistoryResponse>(props.token, props.home.homeId, "daily-usage", fromDate, toDate, props.operatorMode),
        api.getHistory<MonthlyUsageHistoryResponse>(props.token, props.home.homeId, "monthly-usage", fromDate, toDate, props.operatorMode),
        api.getHistory<BillingCycleHistoryResponse>(props.token, props.home.homeId, "billing-cycles", fromDate, toDate, props.operatorMode),
        api.getHistory<MilestoneHistoryResponse>(props.token, props.home.homeId, "milestones", fromDate, toDate, props.operatorMode),
        api.getHistory<AnomalyHistoryResponse>(props.token, props.home.homeId, "anomalies", fromDate, toDate, props.operatorMode)
      ]
    )
      .then(([dailyUsage, monthlyUsage, billingCycles, milestones, anomalies]) => {
        setHistory({ dailyUsage, monthlyUsage, billingCycles, milestones, anomalies });
      })
      .catch((error) => props.onMessage(toMessage(error)));
  }, [props.home.homeId]);

  return (
    <div className="modal-backdrop" onClick={props.onClose}>
      <section className="modal" onClick={(event) => event.stopPropagation()}>
        <button className="close-button" onClick={props.onClose} aria-label="Close details"><X size={18} /></button>
        <p className="eyebrow">House detail</p>
        <h2>{props.home.name}</h2>

        <div className="detail-grid">
          <Metric icon={<BatteryCharging />} label="Current load" value={formatWatts(props.home.billing.currentTotalWatts)} />
          <Metric icon={<PlugZap />} label="Cycle usage" value={formatKwh(props.home.billing.currentCycleUsageKwh)} />
          <Metric icon={<AlertTriangle />} label="Cycle cost" value={formatMoney(props.home.billing.totalCostAmount)} />
        </div>

        <h3>Appliances</h3>
        <div className="appliance-list">
          {props.home.appliances.map((appliance) => (
            <div className={`appliance-row ${appliance.anomalyActive ? "danger" : ""}`} key={appliance.applianceId}>
              <strong>{appliance.name}</strong>
              <span>{formatWatts(appliance.latestWattage)}</span>
              <small>{appliance.typeDisplayName} | safe limit {formatWatts(appliance.safeWattLimit)}</small>
            </div>
          ))}
        </div>

        <h3>History</h3>
        <HomeHistoryAnalytics history={history} />
      </section>
    </div>
  );
}

type HomeDetailHistory = {
  dailyUsage: DailyUsageHistoryResponse;
  monthlyUsage: MonthlyUsageHistoryResponse;
  billingCycles: BillingCycleHistoryResponse;
  milestones: MilestoneHistoryResponse;
  anomalies: AnomalyHistoryResponse;
};

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="metric-card compact">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function HomeHistoryAnalytics({ history }: { history: HomeDetailHistory | null }) {
  if (!history) {
    return <div className="empty-state">Loading historical analysis...</div>;
  }

  const dailyPoints = history.dailyUsage.dailyUsage.map((point) => ({
    label: shortDate(point.usageDate),
    value: Number(point.totalEnergyKwh ?? 0)
  }));
  const peakPoints = history.dailyUsage.dailyUsage.map((point) => ({
    label: shortDate(point.usageDate),
    value: Number(point.peakWatts ?? 0)
  }));
  const latestMonth = history.monthlyUsage.monthlySummaries[history.monthlyUsage.monthlySummaries.length - 1];
  const latestCycle = history.billingCycles.billingCycles[history.billingCycles.billingCycles.length - 1];
  const recentEvents = [
    ...history.milestones.milestoneEvents.map((event) => ({
      kind: event.stage,
      title: `${event.milestone} milestone`,
      detail: `${Number(event.usagePercentageOfLimit ?? 0).toFixed(0)}% of limit`,
      time: event.triggeredAt
    })),
    ...history.anomalies.applianceAnomalies.map((event) => ({
      kind: event.status,
      title: `${event.anomalyType} anomaly`,
      detail: `${formatWatts(event.peakWatts)} peak, ${event.durationSeconds ?? 0}s`,
      time: event.startedAt
    }))
  ].sort((first, second) => second.time.localeCompare(first.time)).slice(0, 5);

  return (
    <div className="home-history-analytics">
      <section className="history-chart-card wide">
        <div className="report-card-heading">
          <strong>Daily energy trend</strong>
          <span>Last 30 days</span>
        </div>
        <SparklineChart points={dailyPoints} unit="kWh" />
      </section>

      <section className="history-chart-card">
        <div className="report-card-heading">
          <strong>Peak watt trend</strong>
          <span>Daily maximum</span>
        </div>
        <SparklineChart points={peakPoints} unit="W" />
      </section>

      <section className="history-chart-card">
        <div className="report-card-heading">
          <strong>Latest summary</strong>
          <span>Historical rollups</span>
        </div>
        <div className="history-summary-list">
          <span>Monthly energy <strong>{latestMonth ? formatKwh(latestMonth.totalEnergyKwh) : "No monthly rollup"}</strong></span>
          <span>Monthly peak day <strong>{latestMonth ? formatKwh(latestMonth.peakDailyKwh) : "No monthly rollup"}</strong></span>
          <span>Last finalized cycle <strong>{latestCycle ? formatKwh(latestCycle.totalUsageKwh) : "No finalized cycle"}</strong></span>
        </div>
      </section>

      <section className="history-chart-card wide">
        <div className="report-card-heading">
          <strong>Recent history events</strong>
          <span>Milestones and anomalies</span>
        </div>
        <div className="history-event-list">
          {recentEvents.map((event, index) => (
            <div className={`history-event-row ${event.kind.toLowerCase()}`} key={`${event.title}-${event.time}-${index}`}>
              <strong>{event.title}</strong>
              <span>{event.detail}</span>
              <small>{formatDateTime(event.time)}</small>
            </div>
          ))}
          {recentEvents.length === 0 && <div className="empty-state">No historical events for this range.</div>}
        </div>
      </section>
    </div>
  );
}

function SparklineChart({ points, unit }: { points: { label: string; value: number }[]; unit: string }) {
  const values = points.length > 0 ? points : [{ label: "No data", value: 0 }];
  const max = Math.max(...values.map((point) => point.value), 1);
  const width = 520;
  const height = 150;
  const padding = 16;
  const plotWidth = width - padding * 2;
  const plotHeight = height - padding * 2;
  const path = values.map((point, index) => {
    const x = padding + (values.length === 1 ? 0 : (index / (values.length - 1)) * plotWidth);
    const y = padding + plotHeight - (point.value / max) * plotHeight;
    return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
  }).join(" ");
  const latest = values[values.length - 1];

  return (
    <div className="sparkline-chart">
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={`Historical ${unit} trend`}>
        <path d={path} />
        {values.map((point, index) => {
          const x = padding + (values.length === 1 ? 0 : (index / (values.length - 1)) * plotWidth);
          const y = padding + plotHeight - (point.value / max) * plotHeight;
          return <circle key={`${point.label}-${index}`} cx={x} cy={y} r="3" />;
        })}
      </svg>
      <div className="trend-chart-footer">
        <span>{values[0]?.label}</span>
        <strong>{latest.value.toFixed(unit === "W" ? 0 : 2)} {unit} latest</strong>
        <span>{latest.label}</span>
      </div>
    </div>
  );
}

function shortDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(new Date(value));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function toMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) return error.message;
  return "Could not load house history.";
}
