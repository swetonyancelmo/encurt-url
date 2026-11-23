CREATE TABLE click_daily(
    short_url_id BIGINT NOT NULL REFERENCES short_url (id) ON DELETE CASCADE,
    day DATE NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (short_url_id, day)
);
