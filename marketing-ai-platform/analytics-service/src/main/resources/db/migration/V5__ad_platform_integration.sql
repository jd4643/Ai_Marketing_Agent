-- ============================================================
-- V5: Ad Platform Integration tables (Meta-first, multi-platform ready)
-- ============================================================

-- 1) ad_platform_connections — one row per connected ad account
CREATE TABLE IF NOT EXISTS ad_platform_connections (
    id                      UUID PRIMARY KEY,
    business_id             UUID NOT NULL REFERENCES business_profile(id),
    platform                TEXT NOT NULL,
    external_business_id    TEXT NULL,
    external_account_id     TEXT NOT NULL,
    connection_name         TEXT NULL,
    access_token_encrypted  TEXT NULL,
    refresh_token_encrypted TEXT NULL,
    token_expires_at        TIMESTAMP NULL,
    scopes_json             JSONB NULL,
    status                  TEXT NOT NULL,
    last_synced_at          TIMESTAMP NULL,
    metadata_json           JSONB NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_apc_business_id ON ad_platform_connections(business_id);
CREATE INDEX IF NOT EXISTS idx_apc_platform ON ad_platform_connections(platform);
CREATE INDEX IF NOT EXISTS idx_apc_external_account_id ON ad_platform_connections(external_account_id);

-- 2) ad_platform_ads — synced ad metadata from external platform
CREATE TABLE IF NOT EXISTS ad_platform_ads (
    id                      UUID PRIMARY KEY,
    business_id             UUID NOT NULL REFERENCES business_profile(id),
    connection_id           UUID NOT NULL REFERENCES ad_platform_connections(id),
    platform                TEXT NOT NULL,
    external_campaign_id    TEXT NULL,
    external_ad_group_id    TEXT NULL,
    external_ad_id          TEXT NOT NULL,
    external_creative_id    TEXT NULL,
    campaign_name           TEXT NULL,
    ad_group_name           TEXT NULL,
    ad_name                 TEXT NULL,
    creative_name           TEXT NULL,
    status                  TEXT NULL,
    effective_status        TEXT NULL,
    objective               TEXT NULL,
    raw_json                JSONB NULL,
    last_seen_at            TIMESTAMP NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_apa_connection_id ON ad_platform_ads(connection_id);
CREATE INDEX IF NOT EXISTS idx_apa_business_id ON ad_platform_ads(business_id);
CREATE INDEX IF NOT EXISTS idx_apa_platform ON ad_platform_ads(platform);
CREATE INDEX IF NOT EXISTS idx_apa_external_ad_id ON ad_platform_ads(external_ad_id);
CREATE INDEX IF NOT EXISTS idx_apa_external_campaign_id ON ad_platform_ads(external_campaign_id);
CREATE INDEX IF NOT EXISTS idx_apa_last_seen_at ON ad_platform_ads(last_seen_at);

-- 3) ad_platform_insights — synced performance data per ad per date range
CREATE TABLE IF NOT EXISTS ad_platform_insights (
    id                      UUID PRIMARY KEY,
    business_id             UUID NOT NULL REFERENCES business_profile(id),
    connection_id           UUID NOT NULL REFERENCES ad_platform_connections(id),
    platform                TEXT NOT NULL,
    external_ad_id          TEXT NOT NULL,
    date_start              DATE NOT NULL,
    date_stop               DATE NOT NULL,
    impressions             BIGINT NULL,
    clicks                  BIGINT NULL,
    spend                   NUMERIC NULL,
    reach                   BIGINT NULL,
    ctr                     NUMERIC NULL,
    cpc                     NUMERIC NULL,
    cpm                     NUMERIC NULL,
    conversions             BIGINT NULL,
    revenue                 NUMERIC NULL,
    roas                    NUMERIC NULL,
    actions_json            JSONB NULL,
    action_values_json      JSONB NULL,
    raw_json                JSONB NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_api_business_id ON ad_platform_insights(business_id);
CREATE INDEX IF NOT EXISTS idx_api_connection_id ON ad_platform_insights(connection_id);
CREATE INDEX IF NOT EXISTS idx_api_platform ON ad_platform_insights(platform);
CREATE INDEX IF NOT EXISTS idx_api_external_ad_id ON ad_platform_insights(external_ad_id);
CREATE INDEX IF NOT EXISTS idx_api_date_start ON ad_platform_insights(date_start);
CREATE INDEX IF NOT EXISTS idx_api_date_stop ON ad_platform_insights(date_stop);

-- 4) creative_asset_platform_mapping — links our creative_assets to external ads
CREATE TABLE IF NOT EXISTS creative_asset_platform_mapping (
    id                      UUID PRIMARY KEY,
    business_id             UUID NOT NULL REFERENCES business_profile(id),
    creative_asset_id       UUID NOT NULL REFERENCES creative_assets(id),
    connection_id           UUID NOT NULL REFERENCES ad_platform_connections(id),
    platform                TEXT NOT NULL,
    external_ad_id          TEXT NULL,
    external_creative_id    TEXT NULL,
    mapping_method          TEXT NOT NULL,
    confidence_score        NUMERIC NULL,
    metadata_json           JSONB NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_capm_creative_asset_id ON creative_asset_platform_mapping(creative_asset_id);
CREATE INDEX IF NOT EXISTS idx_capm_external_ad_id ON creative_asset_platform_mapping(external_ad_id);
CREATE INDEX IF NOT EXISTS idx_capm_external_creative_id ON creative_asset_platform_mapping(external_creative_id);
CREATE INDEX IF NOT EXISTS idx_capm_business_id ON creative_asset_platform_mapping(business_id);
CREATE INDEX IF NOT EXISTS idx_capm_platform ON creative_asset_platform_mapping(platform);
