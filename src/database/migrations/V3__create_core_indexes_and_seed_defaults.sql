SET search_path TO wattsmart, public;

CREATE INDEX idx_homes_status
    ON homes (status);

CREATE INDEX idx_homes_contact_email
    ON homes (contact_email);

CREATE INDEX idx_tariff_plans_effective_window
    ON tariff_plans (effective_from, effective_to);

CREATE INDEX idx_appliances_home_id
    ON appliances (home_id);

CREATE INDEX idx_appliances_type_profile_id
    ON appliances (appliance_type_profile_id);

CREATE INDEX idx_home_event_logs_home_triggered_at
    ON home_event_logs (home_id, triggered_at DESC);

CREATE INDEX idx_home_event_logs_event_type
    ON home_event_logs (event_type, triggered_at DESC);

CREATE INDEX idx_billing_ledger_entries_home_occurred_at
    ON billing_ledger_entries (home_id, occurred_at DESC);

CREATE INDEX idx_consumption_daily_snapshots_home_date
    ON consumption_daily_snapshots (home_id, snapshot_date DESC);

CREATE INDEX idx_consumption_daily_snapshots_appliance_date
    ON consumption_daily_snapshots (appliance_id, snapshot_date DESC)
    WHERE appliance_id IS NOT NULL;

CREATE UNIQUE INDEX uq_consumption_daily_snapshots_home_daily
    ON consumption_daily_snapshots (home_id, snapshot_date)
    WHERE appliance_id IS NULL;

CREATE UNIQUE INDEX uq_consumption_daily_snapshots_appliance_daily
    ON consumption_daily_snapshots (home_id, appliance_id, snapshot_date)
    WHERE appliance_id IS NOT NULL;

CREATE INDEX idx_scheduled_jobs_status_next_run
    ON scheduled_jobs (status, next_run_at);

CREATE INDEX idx_scheduled_job_runs_job_started_at
    ON scheduled_job_runs (scheduled_job_id, started_at DESC);

INSERT INTO tariff_plans (
    code,
    name,
    currency_code,
    base_rate_per_kwh,
    penalty_rate_per_kwh,
    effective_from,
    is_default
) VALUES (
    'TR-DEFAULT-RESIDENTIAL',
    'Default Residential Tariff',
    'TRY',
    2.350000,
    3.525000,
    DATE '2026-07-21',
    TRUE
);

INSERT INTO appliance_type_profiles (
    code,
    display_name,
    description,
    average_watts,
    default_safe_watt_limit,
    peak_watt_limit,
    allowed_deviation_pct,
    default_anomaly_cycle_threshold,
    default_daily_kwh
) VALUES
    ('REFRIGERATOR', 'Refrigerator', 'Cold storage appliance with continuous low-cycle load.', 180.00, 350.00, 500.00, 35.00, 3, 1.800),
    ('AIR_CONDITIONER', 'Air Conditioner', 'Climate control appliance with compressor-based spikes.', 1400.00, 2200.00, 2800.00, 40.00, 3, 12.000),
    ('WASHING_MACHINE', 'Washing Machine', 'Batch-cycle laundry appliance with patterned bursts.', 500.00, 1100.00, 1600.00, 45.00, 3, 2.300),
    ('DISHWASHER', 'Dishwasher', 'Batch-cycle kitchen appliance with heating phases.', 700.00, 1400.00, 1800.00, 40.00, 3, 1.900),
    ('WATER_HEATER', 'Water Heater', 'High-watt heating appliance with predictable duty windows.', 2200.00, 3000.00, 3500.00, 25.00, 2, 8.500),
    ('LIGHTING', 'Lighting', 'Low to medium constant household lighting circuits.', 80.00, 200.00, 350.00, 30.00, 4, 0.900),
    ('TELEVISION', 'Television', 'Consumer display device with moderate steady consumption.', 120.00, 220.00, 300.00, 35.00, 3, 0.500),
    ('OVEN', 'Oven', 'Kitchen heating appliance with short high-power periods.', 2400.00, 3200.00, 3800.00, 20.00, 2, 3.200),
    ('MICROWAVE', 'Microwave', 'Short-duration kitchen appliance with high transient load.', 1200.00, 1600.00, 1900.00, 20.00, 2, 0.350),
    ('GENERIC', 'Generic Appliance', 'Fallback appliance profile when no specific category exists.', 300.00, 700.00, 1000.00, 50.00, 3, 1.000);

INSERT INTO scheduled_jobs (
    job_key,
    name,
    description,
    cron_expression,
    fixed_interval_seconds,
    status,
    handler_name,
    next_run_at
) VALUES
    (
        'daily-home-rollup',
        'Daily Home Consumption Rollup',
        'Aggregates telemetry into durable home and appliance daily snapshots.',
        '0 5 0 * * *',
        NULL,
        'ACTIVE',
        'dailyConsumptionRollupJob',
        TIMESTAMPTZ '2026-07-22 00:05:00+03'
    ),
    (
        'billing-cycle-reconciliation',
        'Billing Cycle Reconciliation',
        'Closes and reopens home billing cycles based on each home configuration.',
        '0 10 0 * * *',
        NULL,
        'ACTIVE',
        'billingCycleReconciliationJob',
        TIMESTAMPTZ '2026-07-22 00:10:00+03'
    ),
    (
        'stale-telemetry-check',
        'Stale Telemetry Check',
        'Flags homes that have stopped sending telemetry updates.',
        NULL,
        300,
        'ACTIVE',
        'staleTelemetryCheckJob',
        TIMESTAMPTZ '2026-07-21 12:05:00+03'
    );
