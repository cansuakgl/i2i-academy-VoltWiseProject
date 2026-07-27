import { AlertTriangle, CircleUser, Bell, ClipboardPlus, Grid2X2, LayoutList, LogOut, Search, Settings, Trash2, WalletCards, X, Zap } from "lucide-react";
import type { AnomalyHistoryResponse, AuthSession, DailyUsageHistoryResponse, HomeStatusItem, RegistrationOptions, TariffPlan } from "../types";
import { HomeDetailModal } from "./HomeDetailModal";
import { HomeRegistrationPanel } from "./HomeRegistrationPanel";
import { HomeMonitoringPanel } from "./HomeMonitoringPanel";
import { NotificationPreferencesPanel } from "./NotificationPreferencesPanel";
import { useEffect, useState } from "react";
import { api, ApiError } from "../api";
import { useMediaQuery } from "../utils/useMediaQuery";
import { formatKwh, formatMoney, formatWatts } from "../utils/format";

type DashboardSection = "monitoring" | "registration" | "tariffs" | "reports" | "alerts" | "settings";

type DashboardPageProps = {
  homes: HomeStatusItem[];
  loading: boolean;
  message: string;
  operatorMode: boolean;
  options: RegistrationOptions | null;
  selectedHome: HomeStatusItem | null;
  session: AuthSession;
  token: string;
  viewMode: "list" | "grid";
  onLogout: () => void;
  onMessage: (message: string) => void;
  onRefresh: () => void;
  onRegistered: () => void;
  onSelectHome: (home: HomeStatusItem | null) => void;
  onSetLoading: (loading: boolean) => void;
  onSetViewMode: (mode: "list" | "grid") => void;
};

export function DashboardPage(props: DashboardPageProps) {
  const [activeSection, setActiveSection] = useState<DashboardSection>("monitoring");
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const isMobile = useMediaQuery("(max-width: 720px)");
  const pageCopy = getPageCopy(activeSection);
  const visibleHomes = filterHomes(props.homes, searchTerm);

  return (
    <main className="app-background">
      <div className={`dashboard-frame ${isMobile ? "mobile-dashboard-frame" : ""}`}>
        {!isMobile && (
          <Sidebar
            activeSection={activeSection}
            isAdmin={props.session.roles.includes("ADMIN")}
            onSectionChange={setActiveSection}
          />
        )}

        <section className="content-shell">
          <header className="topbar">
            <div className="mobile-title-row">
              {isMobile && (
                <div className="brand-mark mobile-brand-mark">
                  <span className="brand-icon"><Zap size={17} fill="currentColor" /></span>
                  <h3>WattSmart</h3>
                </div>
              )}
              <h1>{pageCopy.title}</h1>
            </div>
            
            <div className="topbar-actions">
              <div className={`topbar-search ${searchOpen ? "open" : ""}`}>
                {searchOpen && (
                  <input
                    autoFocus
                    aria-label="Search homes"
                    placeholder="Search homes"
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                  />
                )}
                <button
                  className="round-button"
                  aria-label={searchOpen ? "Close search" : "Search"}
                  onClick={() => {
                    if (searchOpen && searchTerm) {
                      setSearchTerm("");
                      return;
                    }
                    setSearchOpen(!searchOpen);
                  }}
                  type="button"
                >
                  {searchOpen && searchTerm ? <X size={18} /> : <Search size={18} />}
                </button>
              </div>
              <div className="profile-pill">
                <CircleUser size={18} />
                <div>
                  <strong>{props.session.firstName} {props.session.lastName}</strong>
                </div>
              </div>
            </div>
          </header>

          {isMobile && (
            <MobileNavigation
              activeSection={activeSection}
              isAdmin={props.session.roles.includes("ADMIN")}
              onSectionChange={setActiveSection}
            />
          )}

          {props.message && <div className="toast">{props.message}</div>}

          {activeSection === "monitoring" && (
            <HomeMonitoringPanel
              homes={visibleHomes}
              isMobile={isMobile}
              loading={props.loading}
              operatorMode={props.operatorMode}
              viewMode={props.viewMode}
              onRefresh={props.onRefresh}
              onSelectHome={props.onSelectHome}
              onSetViewMode={props.onSetViewMode}
            />
          )}

          {activeSection === "registration" && (
            <HomeRegistrationPanel
              token={props.token}
              isMobile={isMobile}
              options={props.options}
              onMessage={props.onMessage}
              onRegistered={props.onRegistered}
              setLoading={props.onSetLoading}
              variant="page"
            />
          )}

          {activeSection === "tariffs" && (
            <TariffManagementPanel
              token={props.token}
              isAdmin={props.session.roles.includes("ADMIN")}
              onMessage={props.onMessage}
            />
          )}

          {activeSection === "reports" && (
            <ReportsPanel
              homes={visibleHomes}
              operatorMode={props.operatorMode}
              token={props.token}
              onMessage={props.onMessage}
            />
          )}
          {activeSection === "alerts" && <AlertsPanel homes={visibleHomes} />}
          {activeSection === "settings" && (
            <SettingsPanel
              token={props.token}
              onMessage={props.onMessage}
              onLogout={props.onLogout}
              session={props.session}
            />
          )}
        </section>
      </div>

      {props.selectedHome && (
        <HomeDetailModal
          token={props.token}
          home={props.selectedHome}
          operatorMode={props.operatorMode}
          onClose={() => props.onSelectHome(null)}
          onMessage={props.onMessage}
        />
      )}
    </main>
  );
}

function Sidebar({
  activeSection,
  isAdmin,
  onSectionChange
}: {
  activeSection: DashboardSection;
  isAdmin: boolean;
  onSectionChange: (section: DashboardSection) => void;
}) {
  return (
    <aside className="sidebar">
      <div className="brand-mark">
        <span className="brand-icon"><Zap size={18} fill="currentColor" /></span>
        <h3>WattSmart</h3>
      </div>

      <nav className="sidebar-nav" aria-label="Main navigation">
        <NavButton active={activeSection === "monitoring"} icon={<Grid2X2 size={17} />} label="Grid Monitoring" onClick={() => onSectionChange("monitoring")} />
        <NavButton active={activeSection === "registration"} icon={<ClipboardPlus size={17} />} label="Register Home" onClick={() => onSectionChange("registration")} />
        {isAdmin && <NavButton active={activeSection === "tariffs"} icon={<WalletCards size={17} />} label="Manage Tariffs" onClick={() => onSectionChange("tariffs")} />}
        <NavButton active={activeSection === "reports"} icon={<LayoutList size={17} />} label="Reports" onClick={() => onSectionChange("reports")} />
        <NavButton active={activeSection === "alerts"} icon={<Bell size={17} />} label="Alerts" onClick={() => onSectionChange("alerts")} />
      </nav>

      <div className="sidebar-bottom">
        <NavButton
          active={activeSection === "settings"}
          className="settings-nav-item"
          icon={<Settings size={17} />}
          label="Settings"
          onClick={() => onSectionChange("settings")}
        />
      </div>
    </aside>
  );
}

function MobileNavigation({
  activeSection,
  isAdmin,
  onSectionChange
}: {
  activeSection: DashboardSection;
  isAdmin: boolean;
  onSectionChange: (section: DashboardSection) => void;
}) {
  return (
    <nav className="mobile-nav" aria-label="Mobile navigation">
      <NavButton active={activeSection === "monitoring"} icon={<Grid2X2 size={16} />} label="Grid" onClick={() => onSectionChange("monitoring")} />
      <NavButton active={activeSection === "registration"} icon={<ClipboardPlus size={16} />} label="Register" onClick={() => onSectionChange("registration")} />
      {isAdmin && <NavButton active={activeSection === "tariffs"} icon={<WalletCards size={16} />} label="Tariffs" onClick={() => onSectionChange("tariffs")} />}
      <NavButton active={activeSection === "reports"} icon={<LayoutList size={16} />} label="Reports" onClick={() => onSectionChange("reports")} />
      <NavButton active={activeSection === "alerts"} icon={<Bell size={16} />} label="Alerts" onClick={() => onSectionChange("alerts")} />
      <NavButton active={activeSection === "settings"} className="settings-nav-item" icon={<Settings size={16} />} label="Settings" onClick={() => onSectionChange("settings")} />
    </nav>
  );
}

function NavButton(props: { active: boolean; className?: string; icon: React.ReactNode; label: string; onClick: () => void }) {
  return (
    <button className={`nav-item ${props.className ?? ""} ${props.active ? "active" : ""}`} type="button" onClick={props.onClick}>
      {props.icon} {props.label}
    </button>
  );
}

function filterHomes(homes: HomeStatusItem[], searchTerm: string) {
  const query = searchTerm.trim().toLowerCase();
  if (!query) return homes;

  return homes.filter((home) => {
    const homeText = [home.name, home.externalKey, home.status].join(" ").toLowerCase();
    const applianceText = home.appliances
      .map((appliance) => [appliance.name, appliance.applianceCode, appliance.typeCode, appliance.typeDisplayName].join(" "))
      .join(" ")
      .toLowerCase();

    return `${homeText} ${applianceText}`.includes(query);
  });
}

function ReportsPanel(props: {
  homes: HomeStatusItem[];
  operatorMode: boolean;
  token: string;
  onMessage: (message: string) => void;
}) {
  const { homes } = props;
  const [trendRangeDays, setTrendRangeDays] = useState<1 | 7 | 30>(30);
  const [dailyHistory, setDailyHistory] = useState<Record<string, DailyUsageHistoryResponse>>({});
  const [anomalyHistory, setAnomalyHistory] = useState<Record<string, AnomalyHistoryResponse>>({});
  const fromDate = new Date(Date.now() - trendRangeDays * 86400000).toISOString().slice(0, 10);
  const toDate = new Date().toISOString().slice(0, 10);

  useEffect(() => {
    if (homes.length === 0) {
      setDailyHistory({});
      setAnomalyHistory({});
      return;
    }

    Promise.all(
      homes.map((home) =>
        Promise.all([
          api.getHistory<DailyUsageHistoryResponse>(props.token, home.homeId, "daily-usage", fromDate, toDate, props.operatorMode),
          api.getHistory<AnomalyHistoryResponse>(props.token, home.homeId, "anomalies", fromDate, toDate, props.operatorMode)
        ]).then(([dailyUsage, anomalies]) => [home.homeId, dailyUsage, anomalies] as const)
      )
    )
      .then((entries) => {
        setDailyHistory(Object.fromEntries(entries.map(([homeId, dailyUsage]) => [homeId, dailyUsage])));
        setAnomalyHistory(Object.fromEntries(entries.map(([homeId, , anomalies]) => [homeId, anomalies])));
      })
      .catch((error) => props.onMessage(toMessage(error)));
  }, [fromDate, homes, props.operatorMode, props.token, toDate]);

  const totalLoad = homes.reduce((sum, home) => sum + Number(home.billing.currentTotalWatts ?? 0), 0);
  const anomalyHomes = homes.filter((home) => getHomeState(home).key === "anomaly").length;
  const penaltyHomes = homes.filter((home) => home.billing.highestMilestoneStage === "PENALTY").length;
  const warningHomes = homes.filter((home) => getHomeState(home).key === "warning").length;
  const homesAtRisk = anomalyHomes + penaltyHomes + warningHomes;
  const fleetDailyTrend = buildFleetDailyTrend(Object.values(dailyHistory));
  const anomalyIncidentCount = Object.values(anomalyHistory).reduce((sum, history) => sum + history.applianceAnomalies.length, 0);
  const maxLoad = Math.max(...homes.map((home) => Number(home.billing.currentTotalWatts ?? 0)), 1);
  const loadLeaders = [...homes].sort((first, second) => Number(second.billing.currentTotalWatts ?? 0) - Number(first.billing.currentTotalWatts ?? 0));
  const peakHome = loadLeaders[0];
  const historicalHomeTotals = homes
    .map((home) => ({
      home,
      usageKwh: dailyHistory[home.homeId]?.dailyUsage.reduce((sum, point) => sum + Number(point.totalEnergyKwh ?? 0), 0) ?? 0,
      peakWatts: Math.max(...(dailyHistory[home.homeId]?.dailyUsage.map((point) => Number(point.peakWatts ?? 0)) ?? [0])),
      anomalyCount: anomalyHistory[home.homeId]?.applianceAnomalies.length ?? 0
    }))
    .sort((first, second) => second.usageKwh - first.usageKwh);
  const hasHistoricalUsage = historicalHomeTotals.some((item) => item.usageKwh > 0);
  const totalHistoricalUsage = historicalHomeTotals.reduce((sum, item) => sum + item.usageKwh, 0);

  return (
    <section className="content-panel">
      <StatusStrip homesAtRisk={homesAtRisk} anomalyHomes={anomalyHomes} />

      <div className="report-metric-row live-kpi-row">
        <ReportMetric label="Total grid load" value={formatWatts(totalLoad)} />
        <ReportMetric label="Active anomalies" value={anomalyHomes.toString()} />
        <ReportMetric label="Homes at risk" value={homesAtRisk.toString()} />
        <ReportMetric label="Peak contributor" value={peakHome ? peakHome.name : "No homes"} />
      </div>

      <div className="report-layout">
        <section className="report-chart-card full primary-report-card">
          <div className="report-card-heading">
            <strong>Live load ranking</strong>
            <span>Current demand, colored by operating state</span>
          </div>
          <div className="state-bar-list">
            {loadLeaders.map((home) => (
              <StateBarRow
                key={home.homeId}
                label={home.name}
                value={formatWatts(home.billing.currentTotalWatts)}
                percent={(Number(home.billing.currentTotalWatts ?? 0) / maxLoad) * 100}
                state={getHomeState(home)}
              />
            ))}
            {loadLeaders.length === 0 && <div className="empty-state">No homes are visible for this search.</div>}
          </div>
        </section>

        <section className="report-chart-card trend-card">
          <div className="report-card-heading">
            <strong>Fleet energy trend</strong>
            <div className="range-toggle" aria-label="Trend range">
              <button className={trendRangeDays === 1 ? "active" : ""} type="button" onClick={() => setTrendRangeDays(1)}>24h</button>
              <button className={trendRangeDays === 7 ? "active" : ""} type="button" onClick={() => setTrendRangeDays(7)}>7d</button>
              <button className={trendRangeDays === 30 ? "active" : ""} type="button" onClick={() => setTrendRangeDays(30)}>30d</button>
            </div>
          </div>
          <TrendChart
            emptyLabel={`No data for the last ${trendRangeDays === 1 ? "24 hours" : `${trendRangeDays} days`} yet.`}
            points={fleetDailyTrend.map((point) => ({ label: shortDate(point.date), value: point.usageKwh }))}
          />
          <div className="chart-legend">
            <span><i className="legend-line" /> Fleet kWh</span>
            <span>Daily PostgreSQL rollups</span>
          </div>
        </section>

        <section className="report-chart-card contribution-card">
          <div className="report-card-heading">
            <strong>{trendRangeDays}-day contribution</strong>
            <span>Historical share of total energy</span>
          </div>
          {hasHistoricalUsage ? (
            <StackedContribution items={historicalHomeTotals.map((item) => ({
              label: item.home.name,
              value: item.usageKwh,
              state: getHomeState(item.home)
            }))} total={totalHistoricalUsage} />
          ) : (
            <div className="empty-state compact-empty">No historical usage for this range yet.</div>
          )}
        </section>

        <section className="report-chart-card">
          <div className="report-card-heading">
            <strong>Anomaly concentration</strong>
            <span>{trendRangeDays}-day incident counts</span>
          </div>
          <MiniMatrix items={historicalHomeTotals.map(({ home, anomalyCount }) => ({ label: home.name, value: anomalyCount }))} />
        </section>

        <section className="report-chart-card">
          <div className="report-card-heading">
            <strong>History snapshot</strong>
            <span>Derived from PostgreSQL history</span>
          </div>
          <div className="history-snapshot">
            <ReportMetric label="Historical energy" value={formatKwh(totalHistoricalUsage)} />
            <ReportMetric label="Incident records" value={anomalyIncidentCount.toString()} />
          </div>
        </section>

        <section className="report-chart-card wide">
          <div className="report-card-heading">
            <strong>Operational risk table</strong>
            <span>Fixed header with live state and baseline context</span>
          </div>
          <div className="risk-table">
            <div className="risk-table-header">
              <span>Home</span>
              <span>Load</span>
              <span>{trendRangeDays}d kWh</span>
              <span>Peak W</span>
              <span>State</span>
            </div>
            <div className="risk-table-body">
              {historicalHomeTotals.map(({ home, usageKwh, peakWatts, anomalyCount }) => {
                const state = getHomeState(home);
                const hasBaseline = (dailyHistory[home.homeId]?.dailyUsage.length ?? 0) > 0;
                return (
                  <div className={`risk-table-row ${state.key}`} key={home.homeId}>
                    <strong>{home.name}</strong>
                    <span>{formatWatts(home.billing.currentTotalWatts)}</span>
                    <span>{formatKwh(usageKwh)}</span>
                    <span>{hasBaseline ? formatWatts(peakWatts) : "New (no baseline)"}</span>
                    <span><StatePill state={state} />{anomalyCount > 0 ? <small>{anomalyCount} history</small> : null}</span>
                  </div>
                );
              })}
            </div>
          </div>
        </section>
      </div>
    </section>
  );
}

function AlertsPanel({ homes }: { homes: HomeStatusItem[] }) {
  const [dismissedAlertIds, setDismissedAlertIds] = useState<Set<string>>(new Set());
  const alertItems = homes.flatMap((home) =>
    home.appliances
      .filter((appliance) => appliance.anomalyActive || appliance.aboveSafeLimit)
      .map((appliance) => ({
        id: `${home.homeId}-${appliance.applianceId}-anomaly`,
        title: appliance.anomalyActive ? "Active appliance anomaly" : "Safe limit warning",
        homeName: home.name,
        applianceName: appliance.name,
        detail: `${formatWatts(appliance.latestWattage)} against a safe limit of ${formatWatts(appliance.safeWattLimit)}`,
        status: appliance.anomalyActive ? "Active" : "Warning",
        severity: appliance.anomalyActive ? "danger" : "warning",
        time: appliance.lastCapturedAt ? `Last seen ${formatDateTime(appliance.lastCapturedAt)}` : "Waiting for latest telemetry"
      }))
  ).concat(
    homes
      .filter((home) => home.billing.highestMilestoneStage)
      .map((home) => ({
        id: `${home.homeId}-${home.billing.highestMilestoneReached ?? "milestone"}`,
        title: home.billing.highestMilestoneStage === "PENALTY" ? "Billing penalty milestone" : "Usage warning milestone",
        homeName: home.name,
        applianceName: home.billing.highestMilestoneReached ?? "Usage milestone",
        detail: `${formatKwh(home.billing.currentCycleUsageKwh)} this cycle, ${formatMoney(home.billing.totalCostAmount)} total`,
        status: home.billing.highestMilestoneStage === "PENALTY" ? "Penalty" : "Warning",
        severity: home.billing.highestMilestoneStage === "PENALTY" ? "danger" : "warning",
        time: home.billing.lastTelemetryReceivedAt ? `Updated ${formatDateTime(home.billing.lastTelemetryReceivedAt)}` : "No telemetry timestamp"
      }))
  ).filter((alert) => !dismissedAlertIds.has(alert.id));

  function dismissAlert(alertId: string) {
    setDismissedAlertIds((current) => new Set(current).add(alertId));
  }

  return (
    <section className="content-panel">
      <div className="alert-summary-row">
        <ReportMetric label="Visible alerts" value={alertItems.length.toString()} />
        <ReportMetric label="Affected homes" value={new Set(alertItems.map((alert) => alert.homeName)).size.toString()} />
        <ReportMetric label="Penalty homes" value={homes.filter((home) => home.billing.highestMilestoneStage === "PENALTY").length.toString()} />
      </div>

      <div className="alert-history-list">
        {alertItems.map((alert) => (
          <article className={`alert-history-row ${alert.severity}`} key={alert.id}>
            <span className="alert-icon"><AlertTriangle size={18} /></span>
            <div>
              <strong>{alert.title}</strong>
              <span>{alert.homeName} | {alert.applianceName}</span>
              <small>{alert.detail}</small>
            </div>
            <div className="alert-meta">
              <span>{alert.status}</span>
              <small>{alert.time}</small>
            </div>
            <button className="dismiss-alert-button" type="button" aria-label={`Dismiss ${alert.title}`} onClick={() => dismissAlert(alert.id)}>
              <X size={16} />
            </button>
          </article>
        ))}
        {alertItems.length === 0 && <div className="empty-state">No active anomaly alerts right now.</div>}
      </div>
    </section>
  );
}

function ReportMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="report-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

type HomeVisualState = {
  key: "normal" | "warning" | "penalty" | "anomaly";
  label: string;
};

function StatusStrip({ homesAtRisk, anomalyHomes }: { homesAtRisk: number; anomalyHomes: number }) {
  if (homesAtRisk === 0) {
    return <div className="status-strip normal">All systems normal</div>;
  }

  return (
    <div className={`status-strip ${anomalyHomes > 0 ? "danger" : "warning"}`}>
      {homesAtRisk} homes need attention · {anomalyHomes} active anomalies
    </div>
  );
}

function getHomeState(home: HomeStatusItem): HomeVisualState {
  const hasAnomaly = home.appliances.some((appliance) => appliance.anomalyActive || appliance.aboveSafeLimit);
  if (hasAnomaly) return { key: "anomaly", label: "Anomaly" };
  if (home.billing.highestMilestoneStage === "PENALTY") return { key: "penalty", label: "Penalty" };
  if (home.billing.highestMilestoneStage === "WARNING") return { key: "warning", label: "Watch" };
  return { key: "normal", label: "Normal" };
}

function StatePill({ state }: { state: HomeVisualState }) {
  return <span className={`state-pill ${state.key}`}>{state.label}</span>;
}

function StateBarRow({
  label,
  percent,
  state,
  value
}: {
  label: string;
  percent: number;
  state: HomeVisualState;
  value: string;
}) {
  return (
    <div className="state-bar-row">
      <div className="state-bar-copy">
        <span className={`status-dot ${state.key}`} />
        <strong>{label}</strong>
        <StatePill state={state} />
      </div>
      <div className="state-bar-track" aria-hidden="true">
        <span className={state.key} style={{ width: `${Math.max(3, Math.min(100, percent))}%` }} />
      </div>
      <strong className="state-bar-value">{value}</strong>
    </div>
  );
}

function StackedContribution({
  items,
  total
}: {
  items: { label: string; state: HomeVisualState; value: number }[];
  total: number;
}) {
  return (
    <div className="stacked-contribution">
      <div className="stacked-bar" aria-label="Historical usage contribution">
        {items.map((item) => (
          <span
            className={item.state.key}
            key={item.label}
            style={{ width: `${Math.max(2, (item.value / total) * 100)}%` }}
            title={`${item.label}: ${formatKwh(item.value)}`}
          />
        ))}
      </div>
      <div className="contribution-list">
        {items.map((item) => (
          <div key={item.label}>
            <span><i className={item.state.key} />{item.label}</span>
            <strong>{formatKwh(item.value)}</strong>
          </div>
        ))}
      </div>
    </div>
  );
}

function BarRow({ label, value, percent }: { label: string; value: string; percent: number }) {
  return (
    <div className="bar-row">
      <div>
        <strong>{label}</strong>
        <span>{value}</span>
      </div>
      <div className="bar-track" aria-hidden="true">
        <span style={{ width: `${Math.max(3, Math.min(100, percent))}%` }} />
      </div>
    </div>
  );
}

function TrendChart({ emptyLabel, points }: { emptyLabel: string; points: { label: string; value: number }[] }) {
  const hasData = points.some((point) => point.value > 0);
  if (!hasData) {
    return (
      <div className="trend-empty-state">
        <AlertTriangle size={22} />
        <strong>No data yet</strong>
        <span>{emptyLabel}</span>
      </div>
    );
  }

  const values = points;
  const max = Math.max(...values.map((point) => point.value), 1);
  const width = 720;
  const height = 220;
  const padding = 24;
  const plotWidth = width - padding * 2;
  const plotHeight = height - padding * 2;
  const path = values.map((point, index) => {
    const x = padding + (values.length === 1 ? 0 : (index / (values.length - 1)) * plotWidth);
    const y = padding + plotHeight - (point.value / max) * plotHeight;
    return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
  }).join(" ");
  const areaPath = `${path} L ${padding + plotWidth} ${padding + plotHeight} L ${padding} ${padding + plotHeight} Z`;
  const lastPoint = values[values.length - 1];

  return (
    <div className="trend-chart">
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Daily fleet energy trend">
        <path className="trend-area" d={areaPath} />
        <path className="trend-line" d={path} />
        {values.map((point, index) => {
          const x = padding + (values.length === 1 ? 0 : (index / (values.length - 1)) * plotWidth);
          const y = padding + plotHeight - (point.value / max) * plotHeight;
          return <circle key={`${point.label}-${index}`} cx={x} cy={y} r="3.5" />;
        })}
      </svg>
      <div className="trend-chart-footer">
        <span>{values[0]?.label}</span>
        <strong>{formatKwh(lastPoint.value)} latest</strong>
        <span>{lastPoint.label}</span>
      </div>
    </div>
  );
}

function MiniMatrix({ items }: { items: { label: string; value: number }[] }) {
  const max = Math.max(...items.map((item) => item.value), 1);
  return (
    <div className="mini-matrix">
      {items.map((item) => (
        <div className="matrix-row" key={item.label}>
          <span>{item.label}</span>
          <div className="matrix-cells" aria-hidden="true">
            {Array.from({ length: 8 }).map((_, index) => {
              const active = item.value > 0 && index < Math.ceil((item.value / max) * 8);
              return <i className={active ? "active" : ""} key={index} />;
            })}
          </div>
          <strong>{item.value}</strong>
        </div>
      ))}
    </div>
  );
}

function buildFleetDailyTrend(histories: DailyUsageHistoryResponse[]) {
  const byDate = new Map<string, { date: string; usageKwh: number; peakWatts: number }>();

  histories.forEach((history) => {
    history.dailyUsage.forEach((point) => {
      const current = byDate.get(point.usageDate) ?? { date: point.usageDate, usageKwh: 0, peakWatts: 0 };
      current.usageKwh += Number(point.totalEnergyKwh ?? 0);
      current.peakWatts = Math.max(current.peakWatts, Number(point.peakWatts ?? 0));
      byDate.set(point.usageDate, current);
    });
  });

  return [...byDate.values()].sort((first, second) => first.date.localeCompare(second.date));
}

function SettingsPanel(props: {
  token: string;
  session: AuthSession;
  onMessage: (message: string) => void;
  onLogout: () => void;
}) {
  return (
    <section className="settings-grid">
      <section className="settings-row user-type-panel">
        <div className="settings-row-copy">
          <CircleUser size={22} />
          <p>User type: <strong>{formatRoles(props.session.roles)}</strong></p>
        </div>
      </section>
      <NotificationPreferencesPanel token={props.token} onMessage={props.onMessage} />
      <section className="settings-row account-panel">
        <div className="settings-row-copy">
          <LogOut size={22} />
          <p>Sign out of this WattSmart session on this device.</p>
        </div>
        <button className="logout-card" onClick={props.onLogout}>
          <LogOut size={18} />
          <span>Sign out</span>
        </button>
      </section>
    </section>
  );
}

function TariffManagementPanel(props: {
  token: string;
  isAdmin: boolean;
  onMessage: (message: string) => void;
}) {
  const [tariffs, setTariffs] = useState<TariffPlan[]>([]);
  const [form, setForm] = useState({
    code: "",
    name: "",
    description: "",
    currencyCode: "TRY",
    baseRatePerKwh: "2.25",
    effectiveFrom: new Date().toISOString().slice(0, 10)
  });

  useEffect(() => {
    if (!props.isAdmin) return;
    void loadTariffs();
  }, [props.isAdmin, props.token]);

  async function loadTariffs() {
    try {
      setTariffs(await api.listTariffPlans(props.token));
    } catch (error) {
      props.onMessage(toMessage(error));
    }
  }

  async function createTariff(event: React.FormEvent) {
    event.preventDefault();
    try {
      await api.createTariffPlan(props.token, {
        code: form.code,
        name: form.name,
        description: form.description || null,
        currencyCode: form.currencyCode,
        baseRatePerKwh: Number(form.baseRatePerKwh),
        effectiveFrom: form.effectiveFrom,
        active: true,
        milestones: defaultMilestones()
      });
      setForm({
        code: "",
        name: "",
        description: "",
        currencyCode: "TRY",
        baseRatePerKwh: "2.25",
        effectiveFrom: new Date().toISOString().slice(0, 10)
      });
      props.onMessage("Tariff plan added.");
      await loadTariffs();
    } catch (error) {
      props.onMessage(toMessage(error));
    }
  }

  async function deleteTariff(tariffPlanId: string) {
    try {
      await api.deleteTariffPlan(props.token, tariffPlanId);
      props.onMessage("Tariff plan deleted.");
      await loadTariffs();
    } catch (error) {
      props.onMessage(toMessage(error));
    }
  }

  if (!props.isAdmin) {
    return <section className="content-panel"><div className="empty-state">Only admins can manage tariffs.</div></section>;
  }

  return (
    <section className="content-panel tariff-management-panel">
      <form className="tariff-create-form" onSubmit={createTariff}>
        <input required placeholder="Code, e.g. RESIDENTIAL_NIGHT_TR" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} />
        <input required placeholder="Tariff name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
        <input placeholder="Description" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
        <input required maxLength={3} placeholder="TRY" value={form.currencyCode} onChange={(event) => setForm({ ...form, currencyCode: event.target.value.toUpperCase() })} />
        <input required type="number" min="0" step="0.000001" placeholder="Base TRY/kWh" value={form.baseRatePerKwh} onChange={(event) => setForm({ ...form, baseRatePerKwh: event.target.value })} />
        <input required type="date" value={form.effectiveFrom} onChange={(event) => setForm({ ...form, effectiveFrom: event.target.value })} />
        <button className="primary-button tariff-add-button" type="submit">Add tariff</button>
      </form>

      <div className="tariff-admin-list">
        {tariffs.map((tariff) => (
          <article className="tariff-admin-card" key={tariff.tariffPlanId}>
            <div>
              <strong>{tariff.name}</strong>
              <small>{tariff.code} - {tariff.baseRatePerKwh} {tariff.currencyCode}/kWh - {tariff.active ? "Active" : "Inactive"}</small>
              {tariff.description && <p>{tariff.description}</p>}
            </div>
            <button className="icon-danger-button" type="button" aria-label={`Delete ${tariff.name}`} onClick={() => deleteTariff(tariff.tariffPlanId)}>
              <Trash2 size={17} />
            </button>
          </article>
        ))}
        {tariffs.length === 0 && <div className="empty-state">No tariff plans yet.</div>}
      </div>
    </section>
  );
}

function defaultMilestones() {
  return [
    { milestone: "PCT_80", stage: "WARNING", penaltyMultiplier: null },
    { milestone: "PCT_100", stage: "WARNING", penaltyMultiplier: null },
    { milestone: "PCT_120", stage: "PENALTY", penaltyMultiplier: 1.15 },
    { milestone: "PCT_130", stage: "PENALTY", penaltyMultiplier: 1.25 },
    { milestone: "PCT_150", stage: "PENALTY", penaltyMultiplier: 1.5 },
    { milestone: "PCT_180", stage: "PENALTY", penaltyMultiplier: 2 }
  ];
}

function formatRoles(roles: AuthSession["roles"]) {
  return roles.map((role) => role.charAt(0) + role.slice(1).toLowerCase()).join(", ");
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function shortDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(new Date(value));
}

function toMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) return error.message;
  return "Something went wrong.";
}

function getPageCopy(section: DashboardSection): { title: string } {
  switch (section) {
    case "tariffs":
      return {
        title: "Manage Tariffs"
      };
    case "reports":
      return {
        title: "Usage Reports"
      };
    case "alerts":
      return {
        title: "Alert Center"
      };
    case "settings":
      return {
        title: "Settings"
      };
    case "registration":
      return {
        title: "Register Home"
      };
    default:
      return {
        title: "Grid Monitoring"
      };
  }
}
