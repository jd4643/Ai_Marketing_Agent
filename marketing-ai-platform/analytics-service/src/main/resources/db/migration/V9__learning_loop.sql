-- V9: Closed-loop learning — outcome tracking, strategy effectiveness, learning events

-- ─── Recommendation Outcomes ────────────────────────────────────────────────
-- Tracks before/after metrics when a recommendation is applied or dismissed.

CREATE TABLE IF NOT EXISTS recommendation_outcomes (
    id                   UUID PRIMARY KEY,
    recommendation_id    UUID NOT NULL REFERENCES creative_optimization_recommendations(id),
    business_id          UUID NOT NULL REFERENCES business_profile(id),
    action_taken         TEXT NOT NULL,
    action_date          TIMESTAMP NOT NULL,
    baseline_snapshot    JSONB NOT NULL,
    evaluation_window_days INT NOT NULL DEFAULT 7,
    outcome_snapshot     JSONB,
    evaluation_date      TIMESTAMP,
    impact_score         DOUBLE PRECISION,
    outcome_verdict      TEXT NOT NULL DEFAULT 'PENDING',
    delta_json           JSONB,
    notes                TEXT,
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ro_recommendation_id ON recommendation_outcomes(recommendation_id);
CREATE INDEX IF NOT EXISTS idx_ro_business_id ON recommendation_outcomes(business_id);
CREATE INDEX IF NOT EXISTS idx_ro_verdict ON recommendation_outcomes(outcome_verdict);
CREATE INDEX IF NOT EXISTS idx_ro_action_date ON recommendation_outcomes(action_date);

-- ─── Strategy Effectiveness ─────────────────────────────────────────────────
-- Tracks how well a generated strategy performs over time.

CREATE TABLE IF NOT EXISTS strategy_effectiveness (
    id                     UUID PRIMARY KEY,
    strategy_run_id        UUID NOT NULL,
    business_id            UUID NOT NULL REFERENCES business_profile(id),
    evaluation_type        TEXT NOT NULL,
    metrics_at_evaluation  JSONB NOT NULL,
    freshness_score        DOUBLE PRECISION NOT NULL,
    staleness_signals      JSONB,
    recommended_action     TEXT NOT NULL DEFAULT 'KEEP',
    created_at             TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_se_strategy_run_id ON strategy_effectiveness(strategy_run_id);
CREATE INDEX IF NOT EXISTS idx_se_business_id ON strategy_effectiveness(business_id);
CREATE INDEX IF NOT EXISTS idx_se_freshness ON strategy_effectiveness(freshness_score);
CREATE INDEX IF NOT EXISTS idx_se_created_at ON strategy_effectiveness(created_at);

-- ─── Learning Events ────────────────────────────────────────────────────────
-- Central event log for all closed-loop learning signals.

CREATE TABLE IF NOT EXISTS learning_events (
    id                  UUID PRIMARY KEY,
    business_id         UUID NOT NULL REFERENCES business_profile(id),
    event_type          TEXT NOT NULL,
    source_entity_type  TEXT,
    source_entity_id    UUID,
    event_data          JSONB NOT NULL,
    severity            TEXT NOT NULL DEFAULT 'INFO',
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_le_business_id ON learning_events(business_id);
CREATE INDEX IF NOT EXISTS idx_le_event_type ON learning_events(event_type);
CREATE INDEX IF NOT EXISTS idx_le_severity ON learning_events(severity);
CREATE INDEX IF NOT EXISTS idx_le_created_at ON learning_events(created_at);
