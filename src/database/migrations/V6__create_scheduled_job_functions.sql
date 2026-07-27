SET search_path TO wattsmart, public;

CREATE OR REPLACE FUNCTION wattsmart.milestone_threshold_percent(
    p_milestone usage_percentage_milestone
)
RETURNS NUMERIC
LANGUAGE sql
SET search_path = wattsmart, public
IMMUTABLE
AS $$
    SELECT CASE p_milestone
        WHEN 'PCT_80' THEN 80
        WHEN 'PCT_100' THEN 100
        WHEN 'PCT_120' THEN 120
        WHEN 'PCT_130' THEN 130
        WHEN 'PCT_150' THEN 150
        WHEN 'PCT_180' THEN 180
    END;
$$;

CREATE OR REPLACE FUNCTION wattsmart.resolve_usage_milestone(
    p_tariff_plan_id UUID,
    p_usage_percentage NUMERIC
)
RETURNS TABLE (
    milestone usage_percentage_milestone,
    stage milestone_stage,
    penalty_multiplier NUMERIC
)
LANGUAGE sql
SET search_path = wattsmart, public
STABLE
AS $$
    SELECT
        tpm.milestone,
        tpm.stage,
        tpm.penalty_multiplier
    FROM tariff_plan_milestones tpm
    WHERE tpm.tariff_plan_id = p_tariff_plan_id
      AND wattsmart.milestone_threshold_percent(tpm.milestone) <= COALESCE(p_usage_percentage, 0)
    ORDER BY wattsmart.milestone_threshold_percent(tpm.milestone) DESC
    LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION wattsmart.rollup_appliance_usage_daily(
    p_usage_date DATE DEFAULT CURRENT_DATE - 1
)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = wattsmart, public
AS $$
DECLARE
    v_rows_processed INTEGER := 0;
BEGIN
    WITH aggregated AS (
        SELECT
            aur.home_id,
            aur.appliance_id,
            p_usage_date AS usage_date,
            SUM(aur.energy_kwh) AS total_energy_kwh,
            CASE
                WHEN SUM(aur.sample_count) > 0
                    THEN ROUND(
                        SUM(aur.average_watts * aur.sample_count) / SUM(aur.sample_count),
                        2
                    )
                ELSE AVG(aur.average_watts)
            END AS average_watts,
            MAX(COALESCE(aur.peak_watts, aur.average_watts)) AS peak_watts,
            SUM(aur.sample_count) AS sample_count
        FROM appliance_usage_readings aur
        WHERE aur.reading_window_started_at::DATE = p_usage_date
        GROUP BY aur.home_id, aur.appliance_id
    ),
    home_day_totals AS (
        SELECT
            a.home_id,
            SUM(a.total_energy_kwh) AS home_day_energy_kwh
        FROM aggregated a
        GROUP BY a.home_id
    ),
    calculated AS (
        SELECT
            a.home_id,
            a.appliance_id,
            a.usage_date,
            a.total_energy_kwh,
            a.average_watts,
            a.peak_watts,
            CASE
                WHEN htp.monthly_usage_limit_kwh > 0
                    THEN ROUND((a.total_energy_kwh / htp.monthly_usage_limit_kwh) * 100, 2)
                ELSE NULL
            END AS usage_percentage_of_limit,
            ROUND(a.total_energy_kwh * tp.base_rate_per_kwh, 2) AS base_cost_amount,
            ROUND(
                CASE
                    WHEN hdt.home_day_energy_kwh > 0
                        THEN COALESCE(penalty.home_penalty_cost_amount, 0) * (a.total_energy_kwh / hdt.home_day_energy_kwh)
                    ELSE 0
                END,
                2
            ) AS penalty_cost_amount,
            a.sample_count
        FROM aggregated a
        JOIN home_day_totals hdt
            ON hdt.home_id = a.home_id
        JOIN home_tariff_plans htp
            ON htp.home_id = a.home_id
           AND htp.effective_from <= p_usage_date
           AND (htp.effective_to IS NULL OR htp.effective_to >= p_usage_date)
        JOIN tariff_plans tp
            ON tp.id = htp.tariff_plan_id
        JOIN home_billing_accounts hba
            ON hba.home_id = a.home_id
        LEFT JOIN LATERAL (
            SELECT
                COALESCE(SUM(hud.total_energy_kwh), 0) AS cycle_usage_before_day
            FROM home_usage_daily hud
            WHERE hud.home_id = a.home_id
              AND hud.usage_date >= hba.current_cycle_started_on
              AND hud.usage_date < p_usage_date
        ) prior_usage ON TRUE
        LEFT JOIN LATERAL (
            SELECT
                SUM(
                    GREATEST(
                        LEAST(
                            COALESCE(prior_usage.cycle_usage_before_day, 0) + hdt.home_day_energy_kwh,
                            COALESCE(next_threshold.next_threshold_kwh, 999999999999)
                        ) - GREATEST(
                            COALESCE(prior_usage.cycle_usage_before_day, 0),
                            htp.monthly_usage_limit_kwh * wattsmart.milestone_threshold_percent(tpm.milestone) / 100
                        ),
                        0
                    )
                    * tp.base_rate_per_kwh
                    * (tpm.penalty_multiplier - 1)
                ) AS home_penalty_cost_amount
            FROM tariff_plan_milestones tpm
            LEFT JOIN LATERAL (
                SELECT
                    htp.monthly_usage_limit_kwh * wattsmart.milestone_threshold_percent(tpm_next.milestone) / 100 AS next_threshold_kwh
                FROM tariff_plan_milestones tpm_next
                WHERE tpm_next.tariff_plan_id = tpm.tariff_plan_id
                  AND tpm_next.stage = 'PENALTY'
                  AND wattsmart.milestone_threshold_percent(tpm_next.milestone) > wattsmart.milestone_threshold_percent(tpm.milestone)
                ORDER BY wattsmart.milestone_threshold_percent(tpm_next.milestone)
                LIMIT 1
            ) next_threshold ON TRUE
            WHERE tpm.tariff_plan_id = htp.tariff_plan_id
              AND tpm.stage = 'PENALTY'
              AND tpm.penalty_multiplier IS NOT NULL
        ) penalty ON TRUE
    )
    INSERT INTO appliance_usage_daily (
        home_id,
        appliance_id,
        usage_date,
        total_energy_kwh,
        average_watts,
        peak_watts,
        usage_percentage_of_limit,
        base_cost_amount,
        penalty_cost_amount,
        total_cost_amount,
        sample_count
    )
    SELECT
        c.home_id,
        c.appliance_id,
        c.usage_date,
        c.total_energy_kwh,
        c.average_watts,
        c.peak_watts,
        c.usage_percentage_of_limit,
        c.base_cost_amount,
        c.penalty_cost_amount,
        c.base_cost_amount + c.penalty_cost_amount,
        c.sample_count
    FROM calculated c
    ON CONFLICT (home_id, appliance_id, usage_date) DO UPDATE
    SET total_energy_kwh = EXCLUDED.total_energy_kwh,
        average_watts = EXCLUDED.average_watts,
        peak_watts = EXCLUDED.peak_watts,
        usage_percentage_of_limit = EXCLUDED.usage_percentage_of_limit,
        base_cost_amount = EXCLUDED.base_cost_amount,
        penalty_cost_amount = EXCLUDED.penalty_cost_amount,
        total_cost_amount = EXCLUDED.total_cost_amount,
        sample_count = EXCLUDED.sample_count;

    GET DIAGNOSTICS v_rows_processed = ROW_COUNT;
    RETURN v_rows_processed;
END;
$$;

CREATE OR REPLACE FUNCTION wattsmart.rollup_home_usage_daily(
    p_usage_date DATE DEFAULT CURRENT_DATE - 1
)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = wattsmart, public
AS $$
DECLARE
    v_rows_processed INTEGER := 0;
BEGIN
    WITH aggregated AS (
        SELECT
            aud.home_id,
            aud.usage_date,
            SUM(aud.total_energy_kwh) AS total_energy_kwh,
            AVG(aud.average_watts) AS average_watts,
            MAX(aud.peak_watts) AS peak_watts,
            SUM(aud.base_cost_amount) AS base_cost_amount,
            SUM(aud.penalty_cost_amount) AS penalty_cost_amount,
            SUM(aud.total_cost_amount) AS total_cost_amount,
            SUM(aud.sample_count) AS sample_count
        FROM appliance_usage_daily aud
        WHERE aud.usage_date = p_usage_date
        GROUP BY aud.home_id, aud.usage_date
    ),
    calculated AS (
        SELECT
            a.home_id,
            a.usage_date,
            a.total_energy_kwh,
            a.average_watts,
            a.peak_watts,
            CASE
                WHEN htp.monthly_usage_limit_kwh > 0
                    THEN ROUND(((COALESCE(prior_usage.cycle_usage_before_day, 0) + a.total_energy_kwh) / htp.monthly_usage_limit_kwh) * 100, 2)
                ELSE NULL
            END AS usage_percentage_of_limit,
            rm.milestone,
            rm.stage,
            a.base_cost_amount,
            a.penalty_cost_amount,
            a.total_cost_amount,
            a.sample_count
        FROM aggregated a
        JOIN home_tariff_plans htp
            ON htp.home_id = a.home_id
           AND htp.effective_from <= a.usage_date
           AND (htp.effective_to IS NULL OR htp.effective_to >= a.usage_date)
        JOIN home_billing_accounts hba
            ON hba.home_id = a.home_id
        LEFT JOIN LATERAL (
            SELECT
                COALESCE(SUM(hud.total_energy_kwh), 0) AS cycle_usage_before_day
            FROM home_usage_daily hud
            WHERE hud.home_id = a.home_id
              AND hud.usage_date >= hba.current_cycle_started_on
              AND hud.usage_date < a.usage_date
        ) prior_usage ON TRUE
        LEFT JOIN LATERAL wattsmart.resolve_usage_milestone(
            htp.tariff_plan_id,
            CASE
                WHEN htp.monthly_usage_limit_kwh > 0
                    THEN ((COALESCE(prior_usage.cycle_usage_before_day, 0) + a.total_energy_kwh) / htp.monthly_usage_limit_kwh) * 100
                ELSE NULL
            END
        ) rm ON TRUE
    )
    INSERT INTO home_usage_daily (
        home_id,
        usage_date,
        total_energy_kwh,
        average_watts,
        peak_watts,
        usage_percentage_of_limit,
        milestone_reached,
        milestone_stage,
        base_cost_amount,
        penalty_cost_amount,
        total_cost_amount,
        sample_count
    )
    SELECT
        c.home_id,
        c.usage_date,
        c.total_energy_kwh,
        c.average_watts,
        c.peak_watts,
        c.usage_percentage_of_limit,
        c.milestone,
        c.stage,
        c.base_cost_amount,
        c.penalty_cost_amount,
        c.total_cost_amount,
        c.sample_count
    FROM calculated c
    ON CONFLICT (home_id, usage_date) DO UPDATE
    SET total_energy_kwh = EXCLUDED.total_energy_kwh,
        average_watts = EXCLUDED.average_watts,
        peak_watts = EXCLUDED.peak_watts,
        usage_percentage_of_limit = EXCLUDED.usage_percentage_of_limit,
        milestone_reached = EXCLUDED.milestone_reached,
        milestone_stage = EXCLUDED.milestone_stage,
        base_cost_amount = EXCLUDED.base_cost_amount,
        penalty_cost_amount = EXCLUDED.penalty_cost_amount,
        total_cost_amount = EXCLUDED.total_cost_amount,
        sample_count = EXCLUDED.sample_count;

    GET DIAGNOSTICS v_rows_processed = ROW_COUNT;
    RETURN v_rows_processed;
END;
$$;

CREATE OR REPLACE FUNCTION wattsmart.refresh_home_billing_accounts(
    p_as_of_date DATE DEFAULT CURRENT_DATE
)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = wattsmart, public
AS $$
DECLARE
    v_rows_processed INTEGER := 0;
BEGIN
    WITH rolled AS (
        SELECT
            hba.id,
            hba.home_id,
            COALESCE(SUM(hud.total_energy_kwh), 0) AS current_cycle_usage_kwh,
            COALESCE(SUM(hud.base_cost_amount), 0) AS current_cycle_base_cost_amount,
            COALESCE(SUM(hud.penalty_cost_amount), 0) AS current_cycle_penalty_cost_amount,
            COALESCE(SUM(hud.total_cost_amount), 0) AS total_cost_amount,
            MAX(wattsmart.milestone_threshold_percent(hud.milestone_reached)) AS highest_milestone_percent
        FROM home_billing_accounts hba
        LEFT JOIN home_usage_daily hud
            ON hud.home_id = hba.home_id
           AND hud.usage_date >= hba.current_cycle_started_on
           AND hud.usage_date <= COALESCE(hba.current_cycle_ends_on, p_as_of_date)
        GROUP BY hba.id, hba.home_id
    ),
    resolved AS (
        SELECT
            r.id,
            r.current_cycle_usage_kwh,
            r.current_cycle_base_cost_amount,
            r.current_cycle_penalty_cost_amount,
            r.total_cost_amount,
            hud.milestone_reached AS highest_milestone_reached,
            hud.milestone_stage AS highest_milestone_stage
        FROM rolled r
        LEFT JOIN LATERAL (
            SELECT
                hud2.milestone_reached,
                hud2.milestone_stage
            FROM home_usage_daily hud2
            WHERE hud2.home_id = r.home_id
              AND hud2.usage_date >= (
                    SELECT current_cycle_started_on
                    FROM home_billing_accounts hba2
                    WHERE hba2.id = r.id
                )
              AND hud2.usage_date <= COALESCE((
                    SELECT current_cycle_ends_on
                    FROM home_billing_accounts hba3
                    WHERE hba3.id = r.id
                ), p_as_of_date)
            ORDER BY wattsmart.milestone_threshold_percent(hud2.milestone_reached) DESC NULLS LAST
            LIMIT 1
        ) hud ON TRUE
    )
    UPDATE home_billing_accounts hba
    SET current_cycle_usage_kwh = resolved.current_cycle_usage_kwh,
        current_cycle_base_cost_amount = resolved.current_cycle_base_cost_amount,
        current_cycle_penalty_cost_amount = resolved.current_cycle_penalty_cost_amount,
        total_cost_amount = resolved.total_cost_amount,
        highest_milestone_reached = resolved.highest_milestone_reached,
        highest_milestone_stage = resolved.highest_milestone_stage,
        last_rollup_at = NOW()
    FROM resolved
    WHERE hba.id = resolved.id;

    GET DIAGNOSTICS v_rows_processed = ROW_COUNT;
    RETURN v_rows_processed;
END;
$$;

CREATE OR REPLACE FUNCTION wattsmart.finalize_home_billing_cycles(
    p_as_of_date DATE DEFAULT CURRENT_DATE
)
RETURNS TABLE(finalized_home_id UUID, next_cycle_started_on DATE, finalized_timezone_name TEXT)
LANGUAGE plpgsql
SET search_path = wattsmart, public
AS $$
BEGIN
    RETURN QUERY
    WITH due_accounts AS (
        SELECT
            hba.id AS billing_account_id,
            hba.home_id,
            hba.current_cycle_started_on AS cycle_started_on,
            (
                DATE_TRUNC('month', hba.current_cycle_started_on)::DATE
                + INTERVAL '1 month'
                + ((htp.billing_cycle_start_day - 1) * INTERVAL '1 day')
            )::DATE AS next_cycle_started_on,
            (
                DATE_TRUNC('month', hba.current_cycle_started_on)::DATE
                + INTERVAL '1 month'
                + ((htp.billing_cycle_start_day - 1) * INTERVAL '1 day')
                - INTERVAL '1 day'
            )::DATE AS cycle_ended_on,
            htp.tariff_plan_id,
            htp.monthly_usage_limit_kwh,
            htp.billing_cycle_start_day,
            tp.code AS tariff_code,
            tp.name AS tariff_name,
            tp.currency_code,
            tp.base_rate_per_kwh,
            hba.current_cycle_usage_kwh,
            hba.current_cycle_base_cost_amount,
            hba.current_cycle_penalty_cost_amount,
            hba.total_cost_amount,
            hba.highest_milestone_reached,
            hba.highest_milestone_stage,
            h.timezone_name
        FROM home_billing_accounts hba
        JOIN homes h
            ON h.id = hba.home_id
        JOIN LATERAL (
            SELECT htp_inner.*
            FROM home_tariff_plans htp_inner
            WHERE htp_inner.home_id = hba.home_id
              AND htp_inner.effective_from <= COALESCE(hba.current_cycle_ends_on, p_as_of_date)
              AND (htp_inner.effective_to IS NULL OR htp_inner.effective_to >= hba.current_cycle_started_on)
            ORDER BY htp_inner.effective_from DESC
            LIMIT 1
        ) htp ON TRUE
        JOIN tariff_plans tp
            ON tp.id = htp.tariff_plan_id
        WHERE p_as_of_date >= (
                DATE_TRUNC('month', hba.current_cycle_started_on)::DATE
                + INTERVAL '1 month'
                + ((htp.billing_cycle_start_day - 1) * INTERVAL '1 day')
            )::DATE
    ),
    usage_totals AS (
        SELECT
            d.billing_account_id,
            d.current_cycle_usage_kwh AS total_usage_kwh,
            d.current_cycle_base_cost_amount AS total_base_cost_amount,
            d.current_cycle_penalty_cost_amount AS total_penalty_cost_amount,
            d.total_cost_amount AS total_cost_amount
        FROM due_accounts d
    ),
    highest_milestones AS (
        SELECT
            d.billing_account_id,
            d.highest_milestone_reached AS milestone_reached,
            d.highest_milestone_stage AS milestone_stage
        FROM due_accounts d
    ),
    upserted_cycles AS (
        INSERT INTO home_billing_cycles (
            home_id,
            tariff_plan_id,
            cycle_started_on,
            cycle_ended_on,
            billing_cycle_start_day,
            usage_limit_kwh,
            total_usage_kwh,
            total_base_cost_amount,
            total_penalty_cost_amount,
            total_cost_amount,
            highest_milestone_reached,
            highest_milestone_stage,
            applied_tariff_code,
            applied_tariff_name,
            applied_currency_code,
            applied_base_rate_per_kwh,
            finalized_at
        )
        SELECT
            d.home_id,
            d.tariff_plan_id,
            d.cycle_started_on,
            d.cycle_ended_on,
            d.billing_cycle_start_day,
            d.monthly_usage_limit_kwh,
            ut.total_usage_kwh,
            ut.total_base_cost_amount,
            ut.total_penalty_cost_amount,
            ut.total_cost_amount,
            hm.milestone_reached,
            hm.milestone_stage,
            d.tariff_code,
            d.tariff_name,
            d.currency_code,
            d.base_rate_per_kwh,
            NOW()
        FROM due_accounts d
        JOIN usage_totals ut
            ON ut.billing_account_id = d.billing_account_id
        LEFT JOIN highest_milestones hm
            ON hm.billing_account_id = d.billing_account_id
        ON CONFLICT (home_id, cycle_started_on, cycle_ended_on) DO UPDATE
        SET tariff_plan_id = EXCLUDED.tariff_plan_id,
            billing_cycle_start_day = EXCLUDED.billing_cycle_start_day,
            usage_limit_kwh = EXCLUDED.usage_limit_kwh,
            total_usage_kwh = EXCLUDED.total_usage_kwh,
            total_base_cost_amount = EXCLUDED.total_base_cost_amount,
            total_penalty_cost_amount = EXCLUDED.total_penalty_cost_amount,
            total_cost_amount = EXCLUDED.total_cost_amount,
            highest_milestone_reached = EXCLUDED.highest_milestone_reached,
            highest_milestone_stage = EXCLUDED.highest_milestone_stage,
            applied_tariff_code = EXCLUDED.applied_tariff_code,
            applied_tariff_name = EXCLUDED.applied_tariff_name,
            applied_currency_code = EXCLUDED.applied_currency_code,
            applied_base_rate_per_kwh = EXCLUDED.applied_base_rate_per_kwh,
            finalized_at = NOW()
        RETURNING home_billing_cycles.id, home_billing_cycles.home_id, home_billing_cycles.cycle_started_on
    ),
    linked_milestones AS (
        UPDATE home_milestone_events hme
        SET billing_cycle_id = uc.id
        FROM upserted_cycles uc
        WHERE hme.home_id = uc.home_id
          AND hme.billing_cycle_started_on = uc.cycle_started_on
          AND hme.billing_cycle_id IS NULL
        RETURNING hme.id
    ),
    rolled_accounts AS (
        UPDATE home_billing_accounts hba
        SET current_cycle_started_on = d.next_cycle_started_on,
            current_cycle_ends_on = NULL,
            current_cycle_usage_kwh = 0,
            current_cycle_base_cost_amount = 0,
            current_cycle_penalty_cost_amount = 0,
            total_cost_amount = 0,
            highest_milestone_reached = NULL,
            highest_milestone_stage = NULL,
            last_telemetry_received_at = NULL,
            last_rollup_at = NOW()
        FROM due_accounts d
        WHERE hba.id = d.billing_account_id
          AND EXISTS (
              SELECT 1
              FROM upserted_cycles uc
              WHERE uc.home_id = d.home_id
          )
        RETURNING hba.home_id, d.next_cycle_started_on, d.timezone_name
    )
    SELECT
        rolled_accounts.home_id,
        rolled_accounts.next_cycle_started_on,
        rolled_accounts.timezone_name
    FROM rolled_accounts
    CROSS JOIN (
        SELECT COUNT(*) AS linked_milestone_count
        FROM linked_milestones
    ) linked;
END;
$$;

CREATE OR REPLACE FUNCTION wattsmart.rollup_home_usage_monthly(
    p_month_start DATE DEFAULT DATE_TRUNC('month', CURRENT_DATE)::DATE
)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = wattsmart, public
AS $$
DECLARE
    v_month_end DATE := (p_month_start + INTERVAL '1 month - 1 day')::DATE;
    v_rows_processed INTEGER := 0;
BEGIN
    WITH monthly AS (
        SELECT
            hud.home_id,
            p_month_start AS month_start,
            v_month_end AS month_end,
            COALESCE(SUM(hud.total_energy_kwh), 0) AS total_energy_kwh,
            AVG(hud.total_energy_kwh) AS average_daily_kwh,
            MAX(hud.total_energy_kwh) AS peak_daily_kwh,
            COALESCE(SUM(hud.base_cost_amount), 0) AS total_base_cost_amount,
            COALESCE(SUM(hud.penalty_cost_amount), 0) AS total_penalty_cost_amount,
            COALESCE(SUM(hud.total_cost_amount), 0) AS total_cost_amount,
            COUNT(*) AS days_counted
        FROM home_usage_daily hud
        WHERE hud.usage_date >= p_month_start
          AND hud.usage_date <= v_month_end
        GROUP BY hud.home_id
    ),
    with_milestone AS (
        SELECT
            m.*,
            hud.milestone_reached AS highest_milestone_reached,
            hud.milestone_stage AS highest_milestone_stage
        FROM monthly m
        LEFT JOIN LATERAL (
            SELECT
                hud2.milestone_reached,
                hud2.milestone_stage
            FROM home_usage_daily hud2
            WHERE hud2.home_id = m.home_id
              AND hud2.usage_date >= p_month_start
              AND hud2.usage_date <= v_month_end
            ORDER BY wattsmart.milestone_threshold_percent(hud2.milestone_reached) DESC NULLS LAST
            LIMIT 1
        ) hud ON TRUE
    )
    INSERT INTO home_usage_monthly_summaries (
        home_id,
        period_type,
        month_start,
        month_end,
        total_energy_kwh,
        average_daily_kwh,
        peak_daily_kwh,
        total_base_cost_amount,
        total_penalty_cost_amount,
        total_cost_amount,
        highest_milestone_reached,
        highest_milestone_stage,
        days_counted
    )
    SELECT
        wm.home_id,
        'MONTHLY',
        wm.month_start,
        wm.month_end,
        wm.total_energy_kwh,
        wm.average_daily_kwh,
        wm.peak_daily_kwh,
        wm.total_base_cost_amount,
        wm.total_penalty_cost_amount,
        wm.total_cost_amount,
        wm.highest_milestone_reached,
        wm.highest_milestone_stage,
        wm.days_counted
    FROM with_milestone wm
    ON CONFLICT (home_id, period_type, month_start) DO UPDATE
    SET month_end = EXCLUDED.month_end,
        total_energy_kwh = EXCLUDED.total_energy_kwh,
        average_daily_kwh = EXCLUDED.average_daily_kwh,
        peak_daily_kwh = EXCLUDED.peak_daily_kwh,
        total_base_cost_amount = EXCLUDED.total_base_cost_amount,
        total_penalty_cost_amount = EXCLUDED.total_penalty_cost_amount,
        total_cost_amount = EXCLUDED.total_cost_amount,
        highest_milestone_reached = EXCLUDED.highest_milestone_reached,
        highest_milestone_stage = EXCLUDED.highest_milestone_stage,
        days_counted = EXCLUDED.days_counted;

    GET DIAGNOSTICS v_rows_processed = ROW_COUNT;
    RETURN v_rows_processed;
END;
$$;

CREATE OR REPLACE FUNCTION wattsmart.generate_home_llm_monthly_summaries(
    p_month_start DATE DEFAULT DATE_TRUNC('month', CURRENT_DATE)::DATE
)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = wattsmart, public
AS $$
DECLARE
    v_rows_processed INTEGER := 0;
BEGIN
    INSERT INTO home_llm_summaries (
        home_id,
        period_type,
        period_start,
        period_end,
        usage_monthly_summary_id,
        summary_text
    )
    SELECT
        hums.home_id,
        hums.period_type,
        hums.month_start,
        hums.month_end,
        hums.id,
        FORMAT(
            'Home %s summary for %s to %s: total usage %.3f kWh, average daily usage %.3f kWh, peak daily usage %.3f kWh, total cost %.2f, penalty cost %.2f, highest milestone %s (%s).',
            h.name,
            hums.month_start,
            hums.month_end,
            hums.total_energy_kwh,
            COALESCE(hums.average_daily_kwh, 0),
            COALESCE(hums.peak_daily_kwh, 0),
            hums.total_cost_amount,
            hums.total_penalty_cost_amount,
            COALESCE(hums.highest_milestone_reached::TEXT, 'NONE'),
            COALESCE(hums.highest_milestone_stage::TEXT, 'NONE')
        )
    FROM home_usage_monthly_summaries hums
    JOIN homes h
        ON h.id = hums.home_id
    WHERE hums.month_start = p_month_start
    ON CONFLICT (home_id, period_type, period_start, period_end) DO UPDATE
    SET usage_monthly_summary_id = EXCLUDED.usage_monthly_summary_id,
        summary_text = EXCLUDED.summary_text,
        updated_at = NOW();

    GET DIAGNOSTICS v_rows_processed = ROW_COUNT;
    RETURN v_rows_processed;
END;
$$;
