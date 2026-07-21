SET search_path TO wattsmart, public;

CREATE TYPE llm_provider AS ENUM ('GOOGLE_GEMINI', 'OPENAI', 'FALLBACK');
CREATE TYPE llm_session_status AS ENUM ('ACTIVE', 'ARCHIVED', 'FAILED');
CREATE TYPE llm_message_role AS ENUM ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL');
CREATE TYPE llm_recommendation_status AS ENUM ('GENERATED', 'DELIVERED', 'FAILED', 'DISMISSED');

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

CREATE TABLE llm_chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID REFERENCES homes(id) ON DELETE CASCADE,
    user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    prompt_template_id UUID REFERENCES llm_prompt_templates(id) ON DELETE SET NULL,
    provider llm_provider NOT NULL,
    title TEXT,
    session_status llm_session_status NOT NULL DEFAULT 'ACTIVE',
    context_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE llm_chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES llm_chat_sessions(id) ON DELETE CASCADE,
    role llm_message_role NOT NULL,
    content TEXT NOT NULL,
    token_count_input INTEGER,
    token_count_output INTEGER,
    latency_ms INTEGER,
    message_metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_llm_message_input_tokens_non_negative
        CHECK (token_count_input IS NULL OR token_count_input >= 0),
    CONSTRAINT chk_llm_message_output_tokens_non_negative
        CHECK (token_count_output IS NULL OR token_count_output >= 0),
    CONSTRAINT chk_llm_message_latency_non_negative
        CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

CREATE TABLE llm_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    session_id UUID REFERENCES llm_chat_sessions(id) ON DELETE SET NULL,
    event_log_id BIGINT REFERENCES home_event_logs(id) ON DELETE SET NULL,
    provider llm_provider NOT NULL,
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

CREATE TABLE llm_usage_records (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID REFERENCES llm_chat_sessions(id) ON DELETE CASCADE,
    message_id BIGINT REFERENCES llm_chat_messages(id) ON DELETE CASCADE,
    provider llm_provider NOT NULL,
    model_name TEXT NOT NULL,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    estimated_cost NUMERIC(12, 6),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_llm_usage_prompt_tokens_non_negative
        CHECK (prompt_tokens >= 0),
    CONSTRAINT chk_llm_usage_completion_tokens_non_negative
        CHECK (completion_tokens >= 0),
    CONSTRAINT chk_llm_usage_total_tokens_non_negative
        CHECK (total_tokens >= 0),
    CONSTRAINT chk_llm_usage_estimated_cost_non_negative
        CHECK (estimated_cost IS NULL OR estimated_cost >= 0)
);

CREATE INDEX idx_llm_chat_sessions_home_id
    ON llm_chat_sessions (home_id);

CREATE INDEX idx_llm_chat_sessions_user_id
    ON llm_chat_sessions (user_id);

CREATE INDEX idx_llm_chat_messages_session_id_created_at
    ON llm_chat_messages (session_id, created_at);

CREATE INDEX idx_llm_recommendations_home_id_created_at
    ON llm_recommendations (home_id, created_at DESC);

CREATE INDEX idx_llm_usage_records_session_id_recorded_at
    ON llm_usage_records (session_id, recorded_at DESC);

CREATE TRIGGER trg_llm_prompt_templates_set_updated_at
BEFORE UPDATE ON llm_prompt_templates
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_llm_chat_sessions_set_updated_at
BEFORE UPDATE ON llm_chat_sessions
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

INSERT INTO llm_prompt_templates (
    key,
    name,
    description,
    language_code,
    system_prompt,
    user_prompt_template
) VALUES (
    'default-energy-advisory-tr',
    'Default Energy Advisory (TR)',
    'Generates Turkish energy-saving recommendations from quota and anomaly context.',
    'tr',
    'Sen WattSmart enerji asistanisin. Kisa, net ve davranis odakli oneriler ver.',
    'Ev: {{home_name}}\nDurum: {{quota_state}}\nAnomaliler: {{anomalies}}\nToplam tuketim: {{total_energy_kwh}} kWh\nToplam maliyet: {{total_cost}} {{currency_code}}\nKullaniciya Turkce, uygulanabilir tasarruf onerileri yaz.'
);
