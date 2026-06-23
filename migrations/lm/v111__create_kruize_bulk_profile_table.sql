CREATE TABLE IF NOT EXISTS kruize_bulk_profile (
    profile_name VARCHAR(255) PRIMARY KEY,
    clusters JSONB NOT NULL,
    recommendation_settings JSONB NOT NULL,
    webhook_url VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);