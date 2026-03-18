-- V7: Recommendation Action Layer — extend creative_optimization_recommendations for apply/dismiss workflows

-- Add missing columns for action layer
ALTER TABLE creative_optimization_recommendations ADD COLUMN IF NOT EXISTS suggested_next_action TEXT NULL;
ALTER TABLE creative_optimization_recommendations ADD COLUMN IF NOT EXISTS applied_at TIMESTAMP NULL;
ALTER TABLE creative_optimization_recommendations ADD COLUMN IF NOT EXISTS dismissed_at TIMESTAMP NULL;
ALTER TABLE creative_optimization_recommendations ADD COLUMN IF NOT EXISTS metadata_json JSONB NULL;
ALTER TABLE creative_optimization_recommendations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT now();

-- Add created_at index for time-range queries
CREATE INDEX IF NOT EXISTS idx_cor_created_at ON creative_optimization_recommendations(created_at);
