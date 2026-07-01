CREATE TABLE IF NOT EXISTS kruize_bulk_profile (
    profile_name VARCHAR(255) PRIMARY KEY,
    cluster_name VARCHAR(255) NOT NULL,
    datasources JSONB NOT NULL,
    namespaces JSONB NOT NULL,
    labels JSONB,
    experiment_types JSONB NOT NULL,
    metadata_profile VARCHAR(255),
    performance_profile VARCHAR(255) NOT NULL,
    trial_settings JSONB,
    recommendation_settings JSONB NOT NULL,
    webhook_url VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for enabled profiles
CREATE INDEX IF NOT EXISTS idx_bulk_profile_enabled
ON kruize_bulk_profile (enabled)
WHERE enabled = true;

-- Index for cluster lookups
CREATE INDEX IF NOT EXISTS idx_bulk_profile_cluster
ON kruize_bulk_profile (cluster_name);

-- GIN index for JSONB label queries
CREATE INDEX IF NOT EXISTS idx_bulk_profile_labels
ON kruize_bulk_profile USING GIN (labels);