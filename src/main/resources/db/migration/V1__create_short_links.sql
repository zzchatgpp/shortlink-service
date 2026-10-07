CREATE TABLE short_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    original_url VARCHAR(2048) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    last_clicked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_short_links_click_count CHECK (click_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
