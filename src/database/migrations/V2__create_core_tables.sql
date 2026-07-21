SET search_path TO wattsmart, public;

CREATE TYPE home_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');
CREATE TYPE quota_state AS ENUM ('NORMAL', 'WARNING', 'BREACHED');
CREATE TYPE billing_entry_type AS ENUM (
    'BASE_USAGE',
    'PENALTY_USAGE',
    'ADJUSTMENT',
    'CREDIT',
    'DEBIT'
);
CREATE TYPE event_severity AS ENUM ('INFO', 'WARNING', 'CRITICAL');
CREATE TYPE event_type AS ENUM (
    'HOME_REGISTERED',
    'HOME_ACTIVATED',
    'HOME_DEACTIVATED',
    'QUOTA_REACHED_80',
    'QUOTA_REACHED_100',
    'PENALTY_TARIFF_ACTIVATED',
    'APPLIANCE_ANOMALY_DETECTED',
    'APPLIANCE_ANOMALY_RESOLVED',
    'TELEMETRY_GAP_DETECTED',
    'SCHEDULED_JOB_FAILED'
);
CREATE TYPE schedule_job_status AS ENUM ('ACTIVE', 'PAUSED', 'DISABLED');
CREATE TYPE schedule_run_status AS ENUM ('STARTED', 'SUCCEEDED', 'FAILED', 'SKIPPED');

CREATE TABLE tariff_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    base_rate_per_kwh NUMERIC(12, 6) NOT NULL,
    penalty_rate_per_kwh NUMERIC(12, 6) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tariff_positive_base_rate
        CHECK (base_rate_per_kwh >= 0),
    CONSTRAINT chk_tariff_positive_penalty_rate
        CHECK (penalty_rate_per_kwh >= 0),
    CONSTRAINT chk_tariff_penalty_not_less_than_base
        CHECK (penalty_rate_per_kwh >= base_rate_per_kwh),
    CONSTRAINT chk_tariff_effective_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE appliance_type_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    description TEXT,
    average_watts NUMERIC(12, 2) NOT NULL,
    default_safe_watt_limit NUMERIC(12, 2) NOT NULL,
    peak_watt_limit NUMERIC(12, 2),
    allowed_deviation_pct NUMERIC(5, 2) NOT NULL DEFAULT 25.00,
    default_anomaly_cycle_threshold SMALLINT NOT NULL DEFAULT 3,
    default_daily_kwh NUMERIC(12, 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_profile_average_watts_positive
        CHECK (average_watts > 0),
    CONSTRAINT chk_profile_safe_watt_limit_positive
        CHECK (default_safe_watt_limit > 0),
    CONSTRAINT chk_profile_peak_watt_limit_valid
        CHECK (peak_watt_limit IS NULL OR peak_watt_limit >= default_safe_watt_limit),
    CONSTRAINT chk_profile_allowed_deviation_pct
        CHECK (allowed_deviation_pct >= 0 AND allowed_deviation_pct <= 500),
    CONSTRAINT chk_profile_anomaly_cycle_threshold
        CHECK (default_anomaly_cycle_threshold >= 1),
    CONSTRAINT chk_profile_daily_kwh_non_negative
        CHECK (default_daily_kwh IS NULL OR default_daily_kwh >= 0)
);

CREATE TABLE homes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_key TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    contact_email TEXT NOT NULL,
    status home_status NOT NULL DEFAULT 'ACTIVE',
    address_line_1 TEXT,
    address_line_2 TEXT,
    city TEXT,
    region TEXT,
    postal_code TEXT,
    country_code CHAR(2),
    timezone_name TEXT NOT NULL DEFAULT 'Europe/Istanbul',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_homes_contact_email_format
        CHECK (POSITION('@' IN contact_email) > 1)
);

CREATE TABLE home_billing_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL UNIQUE REFERENCES homes(id) ON DELETE CASCADE,
    tariff_plan_id UUID NOT NULL REFERENCES tariff_plans(id),
    monthly_budget_amount NUMERIC(12, 2) NOT NULL,
    monthly_energy_quota_kwh NUMERIC(12, 3),
    quota_warning_threshold_pct NUMERIC(5, 2) NOT NULL DEFAULT 80.00,
    quota_critical_threshold_pct NUMERIC(5, 2) NOT NULL DEFAULT 100.00,
    billing_cycle_start_day SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_budget_amount_non_negative
        CHECK (monthly_budget_amount >= 0),
    CONSTRAINT chk_energy_quota_non_negative
        CHECK (monthly_energy_quota_kwh IS NULL OR monthly_energy_quota_kwh >= 0),
    CONSTRAINT chk_warning_threshold_range
        CHECK (quota_warning_threshold_pct > 0 AND quota_warning_threshold_pct <= quota_critical_threshold_pct),
    CONSTRAINT chk_critical_threshold_range
        CHECK (quota_critical_threshold_pct >= 100 AND quota_critical_threshold_pct <= 999.99),
    CONSTRAINT chk_billing_cycle_day
        CHECK (billing_cycle_start_day BETWEEN 1 AND 28)
);

CREATE TABLE home_billing_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL UNIQUE REFERENCES homes(id) ON DELETE CASCADE,
    current_cycle_started_on DATE NOT NULL,
    current_cycle_energy_kwh NUMERIC(14, 3) NOT NULL DEFAULT 0,
    current_cycle_base_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    current_cycle_penalty_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    quota_state quota_state NOT NULL DEFAULT 'NORMAL',
    penalty_active BOOLEAN NOT NULL DEFAULT FALSE,
    penalty_activated_at TIMESTAMPTZ,
    last_telemetry_received_at TIMESTAMPTZ,
    last_rollup_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_billing_energy_non_negative
        CHECK (current_cycle_energy_kwh >= 0),
    CONSTRAINT chk_billing_base_cost_non_negative
        CHECK (current_cycle_base_cost_amount >= 0),
    CONSTRAINT chk_billing_penalty_cost_non_negative
        CHECK (current_cycle_penalty_cost_amount >= 0),
    CONSTRAINT chk_billing_total_cost_non_negative
        CHECK (total_cost_amount >= 0)
);

CREATE TABLE appliances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    appliance_type_profile_id UUID NOT NULL REFERENCES appliance_type_profiles(id),
    appliance_code TEXT NOT NULL,
    name TEXT NOT NULL,
    manufacturer TEXT,
    model_number TEXT,
    nominal_wattage NUMERIC(12, 2),
    safe_watt_limit NUMERIC(12, 2),
    allowed_deviation_pct NUMERIC(5, 2),
    anomaly_cycle_threshold SMALLINT,
    display_order SMALLINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    installed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_appliance_code_per_home
        UNIQUE (home_id, appliance_code),
    CONSTRAINT chk_nominal_wattage_non_negative
        CHECK (nominal_wattage IS NULL OR nominal_wattage >= 0),
    CONSTRAINT chk_safe_watt_limit_positive
        CHECK (safe_watt_limit IS NULL OR safe_watt_limit > 0),
    CONSTRAINT chk_appliance_allowed_deviation_pct
        CHECK (allowed_deviation_pct IS NULL OR (allowed_deviation_pct >= 0 AND allowed_deviation_pct <= 500)),
    CONSTRAINT chk_appliance_anomaly_cycle_threshold
        CHECK (anomaly_cycle_threshold IS NULL OR anomaly_cycle_threshold >= 1)
);

CREATE TABLE home_event_logs (
    id BIGSERIAL PRIMARY KEY,
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    appliance_id UUID REFERENCES appliances(id) ON DELETE SET NULL,
    event_type event_type NOT NULL,
    severity event_severity NOT NULL,
    title TEXT NOT NULL,
    details TEXT,
    event_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE billing_ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    appliance_id UUID REFERENCES appliances(id) ON DELETE SET NULL,
    entry_type billing_entry_type NOT NULL,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    energy_kwh NUMERIC(14, 3),
    applied_rate_per_kwh NUMERIC(12, 6),
    amount NUMERIC(14, 2) NOT NULL,
    running_balance_amount NUMERIC(14, 2) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_billing_period_range
        CHECK (billing_period_end >= billing_period_start),
    CONSTRAINT chk_billing_energy_non_negative_or_null
        CHECK (energy_kwh IS NULL OR energy_kwh >= 0),
    CONSTRAINT chk_billing_rate_non_negative_or_null
        CHECK (applied_rate_per_kwh IS NULL OR applied_rate_per_kwh >= 0)
);

CREATE TABLE consumption_daily_snapshots (
    id BIGSERIAL PRIMARY KEY,
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    appliance_id UUID REFERENCES appliances(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    total_energy_kwh NUMERIC(14, 3) NOT NULL,
    average_watts NUMERIC(12, 2),
    peak_watts NUMERIC(12, 2),
    total_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    penalty_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    sample_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_snapshot_energy_non_negative
        CHECK (total_energy_kwh >= 0),
    CONSTRAINT chk_snapshot_average_watts_non_negative
        CHECK (average_watts IS NULL OR average_watts >= 0),
    CONSTRAINT chk_snapshot_peak_watts_non_negative
        CHECK (peak_watts IS NULL OR peak_watts >= 0),
    CONSTRAINT chk_snapshot_total_cost_non_negative
        CHECK (total_cost_amount >= 0),
    CONSTRAINT chk_snapshot_penalty_cost_non_negative
        CHECK (penalty_cost_amount >= 0),
    CONSTRAINT chk_snapshot_sample_count_non_negative
        CHECK (sample_count >= 0)
);

CREATE TABLE scheduled_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_key TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    cron_expression TEXT,
    fixed_interval_seconds INTEGER,
    status schedule_job_status NOT NULL DEFAULT 'ACTIVE',
    handler_name TEXT NOT NULL,
    last_started_at TIMESTAMPTZ,
    last_completed_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    last_run_status schedule_run_status,
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_job_schedule_shape
        CHECK (
            (cron_expression IS NOT NULL AND fixed_interval_seconds IS NULL)
            OR (cron_expression IS NULL AND fixed_interval_seconds IS NOT NULL)
        ),
    CONSTRAINT chk_job_interval_positive
        CHECK (fixed_interval_seconds IS NULL OR fixed_interval_seconds > 0)
);

CREATE TABLE scheduled_job_runs (
    id BIGSERIAL PRIMARY KEY,
    scheduled_job_id UUID NOT NULL REFERENCES scheduled_jobs(id) ON DELETE CASCADE,
    status schedule_run_status NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    records_processed INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    run_metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    CONSTRAINT chk_job_run_records_processed_non_negative
        CHECK (records_processed >= 0),
    CONSTRAINT chk_job_run_finished_after_started
        CHECK (finished_at IS NULL OR finished_at >= started_at)
);

CREATE TRIGGER trg_tariff_plans_set_updated_at
BEFORE UPDATE ON tariff_plans
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_appliance_type_profiles_set_updated_at
BEFORE UPDATE ON appliance_type_profiles
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_homes_set_updated_at
BEFORE UPDATE ON homes
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_home_billing_configs_set_updated_at
BEFORE UPDATE ON home_billing_configs
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_home_billing_accounts_set_updated_at
BEFORE UPDATE ON home_billing_accounts
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_appliances_set_updated_at
BEFORE UPDATE ON appliances
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_scheduled_jobs_set_updated_at
BEFORE UPDATE ON scheduled_jobs
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();
