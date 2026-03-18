-- V6: Winner detection & optimization recommendations

-- Add classification columns to creative_asset_performance
ALTER TABLE creative_asset_performance ADD COLUMN IF NOT EXISTS performance_score NUMERIC NULL;
ALTER TABLE creative_asset_performance ADD COLUMN IF NOT EXISTS classification TEXT NULL;
ALTER TABLE creative_asset_performance ADD COLUMN IF NOT EXISTS confidence_score NUMERIC NULL;
ALTER TABLE creative_asset_performance ADD COLUMN IF NOT EXISTS reasoning_json JSONB NULL;
ALTER TABLE creative_asset_performance ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_cap_classification ON creative_asset_performance(classification);
CREATE INDEX IF NOT EXISTS idx_cap_performance_score ON creative_asset_performance(performance_score);

-- Optimization recommendations table
CREATE TABLE IF NOT EXISTS creative_optimization_recommendations (
    id                  UUID PRIMARY KEY,
    business_id         UUID NOT NULL REFERENCES business_profile(id),
    creative_asset_id   UUID NULL REFERENCES creative_assets(id),
    recommendation_type TEXT NOT NULL,
    priority            TEXT NOT NULL,
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    reasoning_json      JSONB NULL,
    status              TEXT NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cor_business_id ON creative_optimization_recommendations(business_id);
CREATE INDEX IF NOT EXISTS idx_cor_creative_asset_id ON creative_optimization_recommendations(creative_asset_id);
CREATE INDEX IF NOT EXISTS idx_cor_recommendation_type ON creative_optimization_recommendations(recommendation_type);
CREATE INDEX IF NOT EXISTS idx_cor_status ON creative_optimization_recommendations(status);
