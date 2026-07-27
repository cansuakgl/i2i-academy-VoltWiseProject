SET search_path TO wattsmart, public;

CREATE TYPE home_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');
CREATE TYPE usage_percentage_milestone AS ENUM (
    'PCT_80',
    'PCT_100',
    'PCT_120',
    'PCT_130',
    'PCT_150',
    'PCT_180'
);
CREATE TYPE milestone_stage AS ENUM ('WARNING', 'PENALTY');
CREATE TYPE appliance_anomaly_type AS ENUM ('SAFE_LIMIT_BREACH');
CREATE TYPE appliance_anomaly_status AS ENUM ('OPEN', 'RESOLVED');
CREATE TYPE schedule_job_status AS ENUM ('ACTIVE', 'PAUSED', 'DISABLED');
CREATE TYPE schedule_run_status AS ENUM ('STARTED', 'SUCCEEDED', 'FAILED', 'SKIPPED');

CREATE TABLE tariff_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    currency_code CHAR(3) NOT NULL,
    base_rate_per_kwh NUMERIC(12, 6) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tariff_positive_base_rate
        CHECK (base_rate_per_kwh >= 0),
    CONSTRAINT chk_tariff_effective_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE tariff_plan_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tariff_plan_id UUID NOT NULL REFERENCES tariff_plans(id) ON DELETE CASCADE,
    milestone usage_percentage_milestone NOT NULL,
    stage milestone_stage NOT NULL,
    penalty_multiplier NUMERIC(8, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tariff_plan_milestone
        UNIQUE (tariff_plan_id, milestone),
    CONSTRAINT chk_tariff_plan_penalty_multiplier
        CHECK (penalty_multiplier IS NULL OR penalty_multiplier >= 1),
    CONSTRAINT chk_tariff_plan_warning_penalty_shape
        CHECK (
            (stage = 'WARNING' AND penalty_multiplier IS NULL)
            OR (stage = 'PENALTY' AND penalty_multiplier IS NOT NULL)
        )
);

CREATE TABLE appliance_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    description TEXT,
    typical_watts NUMERIC(12, 2),
    default_safe_watt_limit NUMERIC(12, 2),
    peak_watt_limit NUMERIC(12, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_appliance_type_typical_watts_non_negative
        CHECK (typical_watts IS NULL OR typical_watts >= 0),
    CONSTRAINT chk_appliance_type_safe_watts_positive
        CHECK (default_safe_watt_limit IS NULL OR default_safe_watt_limit > 0),
    CONSTRAINT chk_appliance_type_peak_watts_valid
        CHECK (
            peak_watt_limit IS NULL
            OR default_safe_watt_limit IS NULL
            OR peak_watt_limit >= default_safe_watt_limit
        )
);

CREATE TABLE homes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_key TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    status home_status NOT NULL DEFAULT 'ACTIVE',
    address_line_1 TEXT,
    address_line_2 TEXT,
    city TEXT,
    region TEXT,
    postal_code TEXT,
    country_code CHAR(2),
    timezone_name TEXT NOT NULL DEFAULT 'Europe/Istanbul',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE home_tariff_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    tariff_plan_id UUID NOT NULL REFERENCES tariff_plans(id),
    monthly_usage_limit_kwh NUMERIC(12, 3) NOT NULL,
    billing_cycle_start_day SMALLINT NOT NULL DEFAULT 1,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_home_tariff_monthly_limit_positive
        CHECK (monthly_usage_limit_kwh > 0),
    CONSTRAINT chk_home_tariff_billing_cycle_day
        CHECK (billing_cycle_start_day BETWEEN 1 AND 28),
    CONSTRAINT chk_home_tariff_effective_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT uq_home_tariff_plan_version
        UNIQUE (home_id, effective_from)
);

CREATE TABLE home_billing_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL UNIQUE REFERENCES homes(id) ON DELETE CASCADE,
    current_cycle_started_on DATE NOT NULL,
    current_cycle_ends_on DATE,
    current_cycle_usage_kwh NUMERIC(14, 3) NOT NULL DEFAULT 0,
    current_cycle_base_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    current_cycle_penalty_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    highest_milestone_reached usage_percentage_milestone,
    highest_milestone_stage milestone_stage,
    last_telemetry_received_at TIMESTAMPTZ,
    last_rollup_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_home_billing_cycle_range
        CHECK (current_cycle_ends_on IS NULL OR current_cycle_ends_on >= current_cycle_started_on),
    CONSTRAINT chk_home_billing_usage_non_negative
        CHECK (current_cycle_usage_kwh >= 0),
    CONSTRAINT chk_home_billing_base_cost_non_negative
        CHECK (current_cycle_base_cost_amount >= 0),
    CONSTRAINT chk_home_billing_penalty_cost_non_negative
        CHECK (current_cycle_penalty_cost_amount >= 0),
    CONSTRAINT chk_home_billing_total_cost_non_negative
        CHECK (total_cost_amount >= 0)
);

CREATE TABLE appliances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    appliance_type_id UUID NOT NULL REFERENCES appliance_types(id),
    appliance_code TEXT NOT NULL,
    name TEXT NOT NULL,
    manufacturer TEXT,
    model_name TEXT,
    nominal_wattage NUMERIC(12, 2),
    safe_watt_limit NUMERIC(12, 2),
    display_order SMALLINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    installed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_appliance_code_per_home
        UNIQUE (home_id, appliance_code),
    CONSTRAINT uq_appliances_home_id_id
        UNIQUE (home_id, id),
    CONSTRAINT chk_appliance_nominal_wattage_non_negative
        CHECK (nominal_wattage IS NULL OR nominal_wattage >= 0),
    CONSTRAINT chk_appliance_safe_watt_limit_positive
        CHECK (safe_watt_limit IS NULL OR safe_watt_limit > 0)
);

CREATE TABLE home_usage_daily (
    id BIGSERIAL PRIMARY KEY,
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    total_energy_kwh NUMERIC(14, 3) NOT NULL,
    average_watts NUMERIC(12, 2),
    peak_watts NUMERIC(12, 2),
    usage_percentage_of_limit NUMERIC(7, 2),
    milestone_reached usage_percentage_milestone,
    milestone_stage milestone_stage,
    base_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    penalty_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    sample_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_home_usage_daily
        UNIQUE (home_id, usage_date),
    CONSTRAINT chk_home_usage_energy_non_negative
        CHECK (total_energy_kwh >= 0),
    CONSTRAINT chk_home_usage_average_watts_non_negative
        CHECK (average_watts IS NULL OR average_watts >= 0),
    CONSTRAINT chk_home_usage_peak_watts_non_negative
        CHECK (peak_watts IS NULL OR peak_watts >= 0),
    CONSTRAINT chk_home_usage_percentage_non_negative
        CHECK (usage_percentage_of_limit IS NULL OR usage_percentage_of_limit >= 0),
    CONSTRAINT chk_home_usage_base_cost_non_negative
        CHECK (base_cost_amount >= 0),
    CONSTRAINT chk_home_usage_penalty_cost_non_negative
        CHECK (penalty_cost_amount >= 0),
    CONSTRAINT chk_home_usage_total_cost_non_negative
        CHECK (total_cost_amount >= 0),
    CONSTRAINT chk_home_usage_sample_count_non_negative
        CHECK (sample_count >= 0)
);

CREATE TABLE appliance_usage_daily (
    id BIGSERIAL PRIMARY KEY,
    home_id UUID NOT NULL,
    appliance_id UUID NOT NULL,
    usage_date DATE NOT NULL,
    total_energy_kwh NUMERIC(14, 3) NOT NULL,
    average_watts NUMERIC(12, 2),
    peak_watts NUMERIC(12, 2),
    usage_percentage_of_limit NUMERIC(7, 2),
    base_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    penalty_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    sample_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_appliance_usage_home
        FOREIGN KEY (home_id) REFERENCES homes(id) ON DELETE CASCADE,
    CONSTRAINT fk_appliance_usage_appliance
        FOREIGN KEY (home_id, appliance_id) REFERENCES appliances(home_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_appliance_usage_daily
        UNIQUE (home_id, appliance_id, usage_date),
    CONSTRAINT chk_appliance_usage_energy_non_negative
        CHECK (total_energy_kwh >= 0),
    CONSTRAINT chk_appliance_usage_average_watts_non_negative
        CHECK (average_watts IS NULL OR average_watts >= 0),
    CONSTRAINT chk_appliance_usage_peak_watts_non_negative
        CHECK (peak_watts IS NULL OR peak_watts >= 0),
    CONSTRAINT chk_appliance_usage_percentage_non_negative
        CHECK (usage_percentage_of_limit IS NULL OR usage_percentage_of_limit >= 0),
    CONSTRAINT chk_appliance_usage_base_cost_non_negative
        CHECK (base_cost_amount >= 0),
    CONSTRAINT chk_appliance_usage_penalty_cost_non_negative
        CHECK (penalty_cost_amount >= 0),
    CONSTRAINT chk_appliance_usage_total_cost_non_negative
        CHECK (total_cost_amount >= 0),
    CONSTRAINT chk_appliance_usage_sample_count_non_negative
        CHECK (sample_count >= 0)
);

CREATE TABLE appliance_usage_readings (
    id BIGSERIAL PRIMARY KEY,
    home_id UUID NOT NULL,
    appliance_id UUID NOT NULL,
    reading_window_started_at TIMESTAMPTZ NOT NULL,
    reading_window_ended_at TIMESTAMPTZ NOT NULL,
    average_watts NUMERIC(12, 2) NOT NULL,
    peak_watts NUMERIC(12, 2),
    energy_kwh NUMERIC(14, 6) NOT NULL,
    sample_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_appliance_usage_readings_home
        FOREIGN KEY (home_id) REFERENCES homes(id) ON DELETE CASCADE,
    CONSTRAINT fk_appliance_usage_readings_appliance
        FOREIGN KEY (home_id, appliance_id) REFERENCES appliances(home_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_appliance_usage_reading_window
        UNIQUE (appliance_id, reading_window_started_at, reading_window_ended_at),
    CONSTRAINT chk_appliance_usage_readings_window_order
        CHECK (reading_window_ended_at > reading_window_started_at),
    CONSTRAINT chk_appliance_usage_readings_average_watts_non_negative
        CHECK (average_watts >= 0),
    CONSTRAINT chk_appliance_usage_readings_peak_watts_non_negative
        CHECK (peak_watts IS NULL OR peak_watts >= 0),
    CONSTRAINT chk_appliance_usage_readings_energy_non_negative
        CHECK (energy_kwh >= 0),
    CONSTRAINT chk_appliance_usage_readings_sample_count_positive
        CHECK (sample_count > 0)
);

CREATE TABLE appliance_anomalies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL,
    appliance_id UUID NOT NULL,
    anomaly_type appliance_anomaly_type NOT NULL,
    status appliance_anomaly_status NOT NULL DEFAULT 'OPEN',
    started_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    breached_safe_watt_limit NUMERIC(12, 2),
    average_watts NUMERIC(12, 2),
    peak_watts NUMERIC(12, 2),
    consecutive_breach_count INTEGER NOT NULL DEFAULT 1,
    duration_seconds INTEGER,
    notification_sent_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_appliance_anomalies_home
        FOREIGN KEY (home_id) REFERENCES homes(id) ON DELETE CASCADE,
    CONSTRAINT fk_appliance_anomalies_appliance
        FOREIGN KEY (home_id, appliance_id) REFERENCES appliances(home_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_appliance_anomalies_resolved_after_started
        CHECK (resolved_at IS NULL OR resolved_at >= started_at),
    CONSTRAINT chk_appliance_anomalies_safe_limit_positive
        CHECK (breached_safe_watt_limit IS NULL OR breached_safe_watt_limit > 0),
    CONSTRAINT chk_appliance_anomalies_average_watts_non_negative
        CHECK (average_watts IS NULL OR average_watts >= 0),
    CONSTRAINT chk_appliance_anomalies_peak_watts_non_negative
        CHECK (peak_watts IS NULL OR peak_watts >= 0),
    CONSTRAINT chk_appliance_anomalies_consecutive_breach_count_positive
        CHECK (consecutive_breach_count > 0),
    CONSTRAINT chk_appliance_anomalies_duration_seconds_non_negative
        CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);

CREATE TABLE home_milestone_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    billing_account_id UUID REFERENCES home_billing_accounts(id) ON DELETE SET NULL,
    billing_cycle_id UUID,
    billing_cycle_started_on DATE NOT NULL,
    usage_date DATE,
    milestone usage_percentage_milestone NOT NULL,
    stage milestone_stage NOT NULL,
    usage_percentage_of_limit NUMERIC(7, 2),
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_home_milestone_events_cycle_milestone
        UNIQUE (home_id, billing_cycle_started_on, milestone),
    CONSTRAINT chk_home_milestone_events_usage_percentage_non_negative
        CHECK (usage_percentage_of_limit IS NULL OR usage_percentage_of_limit >= 0)
);

CREATE TABLE home_billing_cycles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    tariff_plan_id UUID REFERENCES tariff_plans(id) ON DELETE SET NULL,
    cycle_started_on DATE NOT NULL,
    cycle_ended_on DATE NOT NULL,
    billing_cycle_start_day SMALLINT NOT NULL,
    usage_limit_kwh NUMERIC(12, 3) NOT NULL,
    total_usage_kwh NUMERIC(14, 3) NOT NULL DEFAULT 0,
    total_base_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_penalty_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    highest_milestone_reached usage_percentage_milestone,
    highest_milestone_stage milestone_stage,
    applied_tariff_code TEXT,
    applied_tariff_name TEXT,
    applied_currency_code CHAR(3),
    applied_base_rate_per_kwh NUMERIC(12, 6),
    finalized_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_home_billing_cycles_home_period
        UNIQUE (home_id, cycle_started_on, cycle_ended_on),
    CONSTRAINT chk_home_billing_cycles_period_order
        CHECK (cycle_ended_on >= cycle_started_on),
    CONSTRAINT chk_home_billing_cycles_start_day
        CHECK (billing_cycle_start_day BETWEEN 1 AND 28),
    CONSTRAINT chk_home_billing_cycles_usage_limit_positive
        CHECK (usage_limit_kwh > 0),
    CONSTRAINT chk_home_billing_cycles_total_usage_non_negative
        CHECK (total_usage_kwh >= 0),
    CONSTRAINT chk_home_billing_cycles_base_cost_non_negative
        CHECK (total_base_cost_amount >= 0),
    CONSTRAINT chk_home_billing_cycles_penalty_cost_non_negative
        CHECK (total_penalty_cost_amount >= 0),
    CONSTRAINT chk_home_billing_cycles_total_cost_non_negative
        CHECK (total_cost_amount >= 0),
    CONSTRAINT chk_home_billing_cycles_base_rate_non_negative
        CHECK (applied_base_rate_per_kwh IS NULL OR applied_base_rate_per_kwh >= 0)
);

ALTER TABLE home_milestone_events
    ADD CONSTRAINT fk_home_milestone_events_billing_cycle
    FOREIGN KEY (billing_cycle_id) REFERENCES home_billing_cycles(id) ON DELETE SET NULL;

CREATE TABLE scheduled_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_key TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    fixed_interval_seconds INTEGER NOT NULL,
    status schedule_job_status NOT NULL DEFAULT 'ACTIVE',
    handler_name TEXT NOT NULL,
    last_started_at TIMESTAMPTZ,
    last_completed_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    last_run_status schedule_run_status,
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_job_interval_positive
        CHECK (fixed_interval_seconds > 0)
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

CREATE TRIGGER trg_tariff_plan_milestones_set_updated_at
BEFORE UPDATE ON tariff_plan_milestones
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_appliance_types_set_updated_at
BEFORE UPDATE ON appliance_types
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_homes_set_updated_at
BEFORE UPDATE ON homes
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_home_tariff_plans_set_updated_at
BEFORE UPDATE ON home_tariff_plans
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

CREATE TRIGGER trg_appliance_anomalies_set_updated_at
BEFORE UPDATE ON appliance_anomalies
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_scheduled_jobs_set_updated_at
BEFORE UPDATE ON scheduled_jobs
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();
