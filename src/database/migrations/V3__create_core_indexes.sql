SET search_path TO wattsmart, public;

CREATE INDEX idx_homes_status
    ON homes (status);

CREATE INDEX idx_tariff_plans_effective_window
    ON tariff_plans (effective_from, effective_to);

CREATE INDEX idx_tariff_plan_milestones_tariff_plan_id
    ON tariff_plan_milestones (tariff_plan_id);

CREATE INDEX idx_tariff_plan_milestones_stage_milestone
    ON tariff_plan_milestones (stage, milestone);

CREATE INDEX idx_home_tariff_plans_tariff_plan_id
    ON home_tariff_plans (tariff_plan_id);

CREATE INDEX idx_home_tariff_plans_home_effective_window
    ON home_tariff_plans (home_id, effective_from DESC, effective_to);

CREATE UNIQUE INDEX uq_home_tariff_plans_current
    ON home_tariff_plans (home_id)
    WHERE effective_to IS NULL;

CREATE INDEX idx_appliances_home_id
    ON appliances (home_id);

CREATE INDEX idx_appliances_type_id
    ON appliances (appliance_type_id);

CREATE INDEX idx_home_usage_daily_home_date
    ON home_usage_daily (home_id, usage_date DESC);

CREATE INDEX idx_home_usage_daily_milestone
    ON home_usage_daily (milestone_reached, usage_date DESC)
    WHERE milestone_reached IS NOT NULL;

CREATE INDEX idx_appliance_usage_daily_home_date
    ON appliance_usage_daily (home_id, usage_date DESC);

CREATE INDEX idx_appliance_usage_daily_appliance_date
    ON appliance_usage_daily (appliance_id, usage_date DESC);

CREATE INDEX idx_appliance_usage_readings_appliance_window
    ON appliance_usage_readings (appliance_id, reading_window_started_at DESC);

CREATE INDEX idx_appliance_usage_readings_home_window
    ON appliance_usage_readings (home_id, reading_window_started_at DESC);

CREATE INDEX idx_appliance_anomalies_appliance_started_at
    ON appliance_anomalies (appliance_id, started_at DESC);

CREATE INDEX idx_appliance_anomalies_home_started_at
    ON appliance_anomalies (home_id, started_at DESC);

CREATE INDEX idx_appliance_anomalies_status_started_at
    ON appliance_anomalies (status, started_at DESC);

CREATE INDEX idx_home_milestone_events_home_triggered_at
    ON home_milestone_events (home_id, triggered_at DESC);

CREATE INDEX idx_home_milestone_events_home_cycle
    ON home_milestone_events (home_id, billing_cycle_started_on DESC);

CREATE INDEX idx_home_milestone_events_milestone_triggered_at
    ON home_milestone_events (milestone, triggered_at DESC);

CREATE INDEX idx_home_billing_cycles_home_started_on
    ON home_billing_cycles (home_id, cycle_started_on DESC);

CREATE INDEX idx_scheduled_jobs_status_next_run
    ON scheduled_jobs (status, next_run_at);

CREATE INDEX idx_scheduled_job_runs_job_started_at
    ON scheduled_job_runs (scheduled_job_id, started_at DESC);
