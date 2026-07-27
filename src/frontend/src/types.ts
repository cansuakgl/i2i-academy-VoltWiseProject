export type UserRole = "ADMIN" | "OPERATOR" | "RESIDENT";

export type AuthSession = {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: UserRole[];
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  homeIds: string[];
};

export type HomeStatusResponse = {
  homes: HomeStatusItem[];
};

export type HomeStatusItem = {
  homeId: string;
  externalKey: string;
  name: string;
  status: string;
  timezoneName: string;
  billing: BillingStatus;
  appliances: ApplianceStatusItem[];
};

export type BillingStatus = {
  currentCycleStartedOn: string | null;
  currentCycleEndsOn: string | null;
  currentCycleUsageKwh: number;
  currentCycleBaseCostAmount: number;
  currentCyclePenaltyCostAmount: number;
  totalCostAmount: number;
  highestMilestoneReached: string | null;
  highestMilestoneStage: string | null;
  currentTotalWatts: number;
  lastTelemetryReceivedAt: string | null;
  lastRollupAt: string | null;
};

export type ApplianceStatusItem = {
  applianceId: string;
  applianceCode: string;
  name: string;
  typeCode: string;
  typeDisplayName: string;
  typicalWatts: number;
  safeWattLimit: number;
  latestWattage: number;
  aboveSafeLimit: boolean;
  consecutiveBreachCount: number;
  anomalyActive: boolean;
  lastCapturedAt: string | null;
  active: boolean;
};

export type RegistrationOptions = {
  tariffPlans: TariffPlanOption[];
  applianceTypes: ApplianceTypeOption[];
  applianceModelProfiles: ApplianceModelProfileOption[];
};

export type TariffPlanOption = {
  tariffPlanId: string;
  code: string;
  name: string;
  description: string;
  currencyCode: string;
  baseRatePerKwh: number;
  active: boolean;
};

export type TariffPlan = TariffPlanOption & {
  effectiveFrom: string;
  effectiveTo: string | null;
  milestones: TariffPlanMilestone[];
};

export type TariffPlanMilestone = {
  tariffPlanMilestoneId: string;
  milestone: string;
  stage: string;
  penaltyMultiplier: number | null;
};

export type ApplianceTypeOption = {
  applianceTypeId: string;
  code: string;
  displayName: string;
  description: string;
  typicalWatts: number;
  defaultSafeWattLimit: number;
  peakWattLimit: number;
};

export type ApplianceModelProfileOption = {
  applianceModelProfileId: string;
  applianceTypeId: string;
  typeCode: string;
  typeDisplayName: string;
  manufacturer: string;
  modelName: string;
  displayName: string | null;
  nominalWattage: number | null;
  safeWattLimit: number | null;
  peakWattLimit: number | null;
  sourceName: string | null;
};

export type DailyUsagePoint = {
  usageDate: string;
  totalEnergyKwh: number;
  averageWatts: number;
  peakWatts: number;
  usagePercentageOfLimit: number;
  milestoneReached: string | null;
  milestoneStage: string | null;
  baseCostAmount: number;
  penaltyCostAmount: number;
  totalCostAmount: number;
  sampleCount: number;
};

export type MonthlyUsagePoint = {
  monthStart: string;
  monthEnd: string;
  totalEnergyKwh: number;
  averageDailyKwh: number;
  peakDailyKwh: number;
  totalBaseCostAmount: number;
  totalPenaltyCostAmount: number;
  totalCostAmount: number;
  highestMilestoneReached: string | null;
  highestMilestoneStage: string | null;
  daysCounted: number;
};

export type BillingCycleItem = {
  billingCycleId: string;
  tariffPlanId: string;
  cycleStartedOn: string;
  cycleEndedOn: string;
  billingCycleStartDay: number;
  usageLimitKwh: number;
  totalUsageKwh: number;
  totalBaseCostAmount: number;
  totalPenaltyCostAmount: number;
  totalCostAmount: number;
  highestMilestoneReached: string | null;
  highestMilestoneStage: string | null;
  appliedTariffCode: string;
  appliedTariffName: string;
  appliedCurrencyCode: string;
  appliedBaseRatePerKwh: number;
  finalizedAt: string;
};

export type MilestoneItem = {
  milestone: string;
  stage: string;
  usagePercentageOfLimit: number;
  usageDate: string;
  triggeredAt: string;
};

export type AnomalyItem = {
  applianceId: string;
  anomalyType: string;
  status: string;
  startedAt: string;
  resolvedAt: string | null;
  breachedSafeWattLimit: number;
  averageWatts: number;
  peakWatts: number;
  consecutiveBreachCount: number;
  durationSeconds: number | null;
  notificationSentAt: string | null;
  notes: string | null;
};

export type DailyUsageHistoryResponse = {
  homeId: string;
  fromDate: string;
  toDate: string;
  dailyUsage: DailyUsagePoint[];
};

export type MonthlyUsageHistoryResponse = {
  homeId: string;
  fromDate: string;
  toDate: string;
  monthlySummaries: MonthlyUsagePoint[];
};

export type BillingCycleHistoryResponse = {
  homeId: string;
  fromDate: string;
  toDate: string;
  billingCycles: BillingCycleItem[];
};

export type MilestoneHistoryResponse = {
  homeId: string;
  fromDate: string;
  toDate: string;
  milestoneEvents: MilestoneItem[];
};

export type AnomalyHistoryResponse = {
  homeId: string;
  fromDate: string;
  toDate: string;
  applianceAnomalies: AnomalyItem[];
};
