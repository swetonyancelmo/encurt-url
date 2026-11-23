CREATE TABLE click_event(
    id BIGSERIAL PRIMARY KEY,
    short_url_id BIGINT NOT NULL REFERENCES short_url (id) ON DELETE CASCADE,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    referrer TEXT NULL,
    user_agent TEXT NULL,
    ip_hash VARCHAR(64) NULL
);

CREATE INDEX idx_click_event_short_url_occurred ON click_event (short_url_id, occurred_at);
