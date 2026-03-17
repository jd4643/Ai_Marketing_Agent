CREATE TABLE IF NOT EXISTS creative_assets (
    id                  UUID PRIMARY KEY,
    business_id         UUID NOT NULL REFERENCES business_profile(id),
    creative_id         UUID NULL REFERENCES creatives(id),
    strategy_request_id UUID NULL,
    asset_type          TEXT NOT NULL,
    platform            TEXT NULL,
    prompt_text         TEXT NOT NULL,
    provider            TEXT NOT NULL,
    provider_asset_id   TEXT NULL,
    asset_url           TEXT NULL,
    thumbnail_url       TEXT NULL,
    status              TEXT NOT NULL,
    trend_context_json  JSONB NULL,
    metadata_json       JSONB NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_creative_assets_business_id ON creative_assets(business_id);
CREATE INDEX IF NOT EXISTS idx_creative_assets_strategy_request_id ON creative_assets(strategy_request_id);
CREATE INDEX IF NOT EXISTS idx_creative_assets_created_at ON creative_assets(created_at);
