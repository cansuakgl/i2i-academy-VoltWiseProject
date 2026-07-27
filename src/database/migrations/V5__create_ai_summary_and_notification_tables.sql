SET search_path TO wattsmart, public;

CREATE TYPE llm_recommendation_status AS ENUM ('GENERATED', 'DELIVERED', 'FAILED', 'DISMISSED');
CREATE TYPE llm_recommendation_trigger AS ENUM ('MONTHLY_SUMMARY', 'USAGE_MILESTONE', 'ANOMALY_ALERT', 'ON_DEMAND');
CREATE TYPE summary_period_type AS ENUM ('MONTHLY');
CREATE TYPE email_notification_type AS ENUM (
    'USAGE_MILESTONE',
    'ANOMALY_ALERT',
    'MONTHLY_SUMMARY',
    'LLM_RECOMMENDATION'
);
CREATE TYPE email_notification_status AS ENUM ('PENDING', 'SENT', 'FAILED', 'CANCELLED');

CREATE TABLE llm_prompt_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT,
    language_code VARCHAR(8) NOT NULL DEFAULT 'tr',
    system_prompt TEXT NOT NULL,
    user_prompt_template TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE home_usage_monthly_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    period_type summary_period_type NOT NULL DEFAULT 'MONTHLY',
    month_start DATE NOT NULL,
    month_end DATE NOT NULL,
    total_energy_kwh NUMERIC(14, 3) NOT NULL DEFAULT 0,
    average_daily_kwh NUMERIC(14, 3),
    peak_daily_kwh NUMERIC(14, 3),
    total_base_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_penalty_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_cost_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    highest_milestone_reached usage_percentage_milestone,
    highest_milestone_stage milestone_stage,
    days_counted INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_home_usage_monthly_summary
        UNIQUE (home_id, period_type, month_start),
    CONSTRAINT chk_home_usage_monthly_range
        CHECK (month_end >= month_start),
    CONSTRAINT chk_home_usage_monthly_energy_non_negative
        CHECK (total_energy_kwh >= 0),
    CONSTRAINT chk_home_usage_monthly_average_daily_non_negative
        CHECK (average_daily_kwh IS NULL OR average_daily_kwh >= 0),
    CONSTRAINT chk_home_usage_monthly_peak_daily_non_negative
        CHECK (peak_daily_kwh IS NULL OR peak_daily_kwh >= 0),
    CONSTRAINT chk_home_usage_monthly_base_cost_non_negative
        CHECK (total_base_cost_amount >= 0),
    CONSTRAINT chk_home_usage_monthly_penalty_cost_non_negative
        CHECK (total_penalty_cost_amount >= 0),
    CONSTRAINT chk_home_usage_monthly_total_cost_non_negative
        CHECK (total_cost_amount >= 0),
    CONSTRAINT chk_home_usage_monthly_days_counted_non_negative
        CHECK (days_counted >= 0)
);

CREATE TABLE home_llm_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    period_type summary_period_type NOT NULL DEFAULT 'MONTHLY',
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    usage_monthly_summary_id UUID REFERENCES home_usage_monthly_summaries(id) ON DELETE SET NULL,
    prompt_template_id UUID REFERENCES llm_prompt_templates(id) ON DELETE SET NULL,
    summary_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_home_llm_summary_period
        UNIQUE (home_id, period_type, period_start, period_end),
    CONSTRAINT chk_home_llm_summary_range
        CHECK (period_end >= period_start)
);

CREATE TABLE llm_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    prompt_template_id UUID REFERENCES llm_prompt_templates(id) ON DELETE SET NULL,
    home_llm_summary_id UUID REFERENCES home_llm_summaries(id) ON DELETE SET NULL,
    source_milestone_event_id UUID REFERENCES home_milestone_events(id) ON DELETE SET NULL,
    source_anomaly_id UUID REFERENCES appliance_anomalies(id) ON DELETE SET NULL,
    trigger_type llm_recommendation_trigger NOT NULL,
    status llm_recommendation_status NOT NULL DEFAULT 'GENERATED',
    recipient_email TEXT NOT NULL,
    language_code VARCHAR(8) NOT NULL DEFAULT 'tr',
    prompt_snapshot TEXT NOT NULL,
    response_text TEXT NOT NULL,
    delivery_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ,
    CONSTRAINT chk_llm_recommendation_email_format
        CHECK (POSITION('@' IN recipient_email) > 1)
);

CREATE TABLE email_notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    subject_template TEXT NOT NULL,
    body_template TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE email_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    notification_template_id UUID REFERENCES email_notification_templates(id) ON DELETE SET NULL,
    llm_recommendation_id UUID REFERENCES llm_recommendations(id) ON DELETE SET NULL,
    home_llm_summary_id UUID REFERENCES home_llm_summaries(id) ON DELETE SET NULL,
    notification_type email_notification_type NOT NULL,
    status email_notification_status NOT NULL DEFAULT 'PENDING',
    source_usage_date DATE,
    milestone usage_percentage_milestone,
    milestone_stage milestone_stage,
    recipient_email TEXT NOT NULL,
    subject_text TEXT NOT NULL,
    body_text TEXT NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_email_notifications_email_format
        CHECK (POSITION('@' IN recipient_email) > 1)
);

CREATE TABLE email_notification_deliveries (
    id BIGSERIAL PRIMARY KEY,
    email_notification_id UUID NOT NULL REFERENCES email_notifications(id) ON DELETE CASCADE,
    status email_notification_status NOT NULL,
    provider_message_id TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    response_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    error_message TEXT
);

CREATE INDEX idx_home_usage_monthly_summaries_home_month
    ON home_usage_monthly_summaries (home_id, month_start DESC);

CREATE INDEX idx_home_llm_summaries_home_period
    ON home_llm_summaries (home_id, period_start DESC);

CREATE INDEX idx_llm_recommendations_home_id_created_at
    ON llm_recommendations (home_id, created_at DESC);

CREATE INDEX idx_llm_recommendations_user_id_created_at
    ON llm_recommendations (user_id, created_at DESC);

CREATE INDEX idx_llm_recommendations_prompt_template_id
    ON llm_recommendations (prompt_template_id);

CREATE UNIQUE INDEX uq_llm_recommendations_monthly_user
    ON llm_recommendations (home_llm_summary_id, user_id, trigger_type)
    WHERE trigger_type = 'MONTHLY_SUMMARY' AND home_llm_summary_id IS NOT NULL;

CREATE UNIQUE INDEX uq_llm_recommendations_milestone_user
    ON llm_recommendations (source_milestone_event_id, user_id, trigger_type)
    WHERE trigger_type = 'USAGE_MILESTONE' AND source_milestone_event_id IS NOT NULL;

CREATE UNIQUE INDEX uq_llm_recommendations_anomaly_user
    ON llm_recommendations (source_anomaly_id, user_id, trigger_type)
    WHERE trigger_type = 'ANOMALY_ALERT' AND source_anomaly_id IS NOT NULL;

CREATE INDEX idx_email_notifications_status_scheduled_for
    ON email_notifications (status, scheduled_for);

CREATE INDEX idx_email_notifications_home_created_at
    ON email_notifications (home_id, created_at DESC);

CREATE INDEX idx_email_notifications_user_created_at
    ON email_notifications (user_id, created_at DESC);

CREATE UNIQUE INDEX uq_email_notifications_milestone_per_user_day
    ON email_notifications (notification_type, home_id, user_id, source_usage_date, milestone)
    WHERE notification_type = 'USAGE_MILESTONE';

CREATE INDEX idx_email_notification_deliveries_notification_attempted_at
    ON email_notification_deliveries (email_notification_id, attempted_at DESC);

CREATE TRIGGER trg_llm_prompt_templates_set_updated_at
BEFORE UPDATE ON llm_prompt_templates
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_home_usage_monthly_summaries_set_updated_at
BEFORE UPDATE ON home_usage_monthly_summaries
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_home_llm_summaries_set_updated_at
BEFORE UPDATE ON home_llm_summaries
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_email_notification_templates_set_updated_at
BEFORE UPDATE ON email_notification_templates
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_email_notifications_set_updated_at
BEFORE UPDATE ON email_notifications
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();
