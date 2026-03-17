CREATE TABLE IF NOT EXISTS creative_asset_performance (
    id                UUID PRIMARY KEY,
    creative_asset_id UUID NOT NULL REFERENCES creative_assets(id),
    business_id       UUID NOT NULL REFERENCES business_profile(id),
    platform          TEXT NOT NULL,
    impressions       BIGINT NULL,
    clicks            BIGINT NULL,
    conversions       BIGINT NULL,
    spend             NUMERIC NULL,
    revenue           NUMERIC NULL,
    ctr               NUMERIC NULL,
    cpc               NUMERIC NULL,
    cpa               NUMERIC NULL,
    roas              NUMERIC NULL,
    recorded_at       TIMESTAMP NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cap_creative_asset_id ON creative_asset_performance(creative_asset_id);
CREATE INDEX IF NOT EXISTS idx_cap_business_id ON creative_asset_performance(business_id);
CREATE INDEX IF NOT EXISTS idx_cap_platform ON creative_asset_performance(platform);
CREATE INDEX IF NOT EXISTS idx_cap_recorded_at ON creative_asset_performance(recorded_at);
