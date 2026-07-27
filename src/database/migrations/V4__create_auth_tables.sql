SET search_path TO wattsmart, public;

CREATE TYPE auth_provider AS ENUM ('LOCAL', 'GOOGLE', 'GITHUB');
CREATE TYPE user_role AS ENUM ('ADMIN', 'OPERATOR', 'RESIDENT');
CREATE TYPE user_status AS ENUM ('PENDING', 'ACTIVE', 'LOCKED', 'DISABLED');

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    auth_provider auth_provider NOT NULL DEFAULT 'LOCAL',
    email_verified_at TIMESTAMPTZ,
    status user_status NOT NULL DEFAULT 'PENDING',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_email_format
        CHECK (POSITION('@' IN email) > 1),
    CONSTRAINT chk_local_user_password_required
        CHECK (
            (auth_provider = 'LOCAL' AND password_hash IS NOT NULL)
            OR (auth_provider <> 'LOCAL')
        )
);

CREATE TABLE user_role_assignments (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role user_role NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE home_user_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    home_id UUID NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invited_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_home_user_membership
        UNIQUE (home_id, user_id)
);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash TEXT NOT NULL UNIQUE,
    ip_address INET,
    user_agent TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    CONSTRAINT chk_user_sessions_expires_after_create
        CHECK (expires_at > created_at)
);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_password_reset_expires_after_create
        CHECK (expires_at > created_at)
);

CREATE TABLE user_notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    usage_milestone_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    anomaly_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    monthly_summary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_home_user_memberships_home_id
    ON home_user_memberships (home_id);

CREATE INDEX idx_home_user_memberships_user_id
    ON home_user_memberships (user_id);

CREATE INDEX idx_user_sessions_user_id
    ON user_sessions (user_id);

CREATE INDEX idx_user_sessions_expires_at
    ON user_sessions (expires_at);

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_home_user_memberships_set_updated_at
BEFORE UPDATE ON home_user_memberships
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();

CREATE TRIGGER trg_user_notification_preferences_set_updated_at
BEFORE UPDATE ON user_notification_preferences
FOR EACH ROW
EXECUTE FUNCTION wattsmart.set_updated_at();
