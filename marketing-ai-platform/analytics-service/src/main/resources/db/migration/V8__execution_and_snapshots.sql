-- V8: Execution plans, tasks, task runs, and performance snapshots

-- ─── Execution Plans ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS execution_plans (
    id              UUID PRIMARY KEY,
    business_id     UUID NOT NULL REFERENCES business_profile(id),
    strategy_run_id UUID NULL,
    name            TEXT NOT NULL,
    description     TEXT NULL,
    source_type     TEXT NOT NULL DEFAULT 'STRATEGY',
    source_json     JSONB NULL,
    status          TEXT NOT NULL DEFAULT 'DRAFT',
    total_tasks     INT NOT NULL DEFAULT 0,
    completed_tasks INT NOT NULL DEFAULT 0,
    failed_tasks    INT NOT NULL DEFAULT 0,
    skipped_tasks   INT NOT NULL DEFAULT 0,
    version         INT NOT NULL DEFAULT 1,
    started_at      TIMESTAMP NULL,
    completed_at    TIMESTAMP NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ep_business_id ON execution_plans(business_id);
CREATE INDEX IF NOT EXISTS idx_ep_status ON execution_plans(status);
CREATE INDEX IF NOT EXISTS idx_ep_strategy_run_id ON execution_plans(strategy_run_id);
CREATE INDEX IF NOT EXISTS idx_ep_created_at ON execution_plans(created_at);

-- ─── Execution Tasks ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS execution_tasks (
    id                    UUID PRIMARY KEY,
    plan_id               UUID NOT NULL REFERENCES execution_plans(id) ON DELETE CASCADE,
    business_id           UUID NOT NULL REFERENCES business_profile(id),
    recommendation_id     UUID NULL REFERENCES creative_optimization_recommendations(id),
    depends_on_task_id    UUID NULL REFERENCES execution_tasks(id),
    task_type             TEXT NOT NULL,
    name                  TEXT NOT NULL,
    description           TEXT NULL,
    input_json            JSONB NULL,
    output_json           JSONB NULL,
    status                TEXT NOT NULL DEFAULT 'PENDING',
    priority              INT NOT NULL DEFAULT 0,
    sequence_order        INT NOT NULL DEFAULT 0,
    max_retries           INT NOT NULL DEFAULT 3,
    retry_count           INT NOT NULL DEFAULT 0,
    error_message         TEXT NULL,
    version               INT NOT NULL DEFAULT 1,
    started_at            TIMESTAMP NULL,
    completed_at          TIMESTAMP NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_et_plan_id ON execution_tasks(plan_id);
CREATE INDEX IF NOT EXISTS idx_et_business_id ON execution_tasks(business_id);
CREATE INDEX IF NOT EXISTS idx_et_status ON execution_tasks(status);
CREATE INDEX IF NOT EXISTS idx_et_task_type ON execution_tasks(task_type);
CREATE INDEX IF NOT EXISTS idx_et_depends_on ON execution_tasks(depends_on_task_id);
CREATE INDEX IF NOT EXISTS idx_et_plan_sequence ON execution_tasks(plan_id, sequence_order);

-- ─── Execution Task Runs ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS execution_task_runs (
    id          UUID PRIMARY KEY,
    task_id     UUID NOT NULL REFERENCES execution_tasks(id) ON DELETE CASCADE,
    attempt     INT NOT NULL DEFAULT 1,
    status      TEXT NOT NULL DEFAULT 'RUNNING',
    input_json  JSONB NULL,
    output_json JSONB NULL,
    error_message TEXT NULL,
    started_at  TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_etr_task_id ON execution_task_runs(task_id);
CREATE INDEX IF NOT EXISTS idx_etr_status ON execution_task_runs(status);

-- ─── Performance Snapshots ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS performance_snapshots (
    id              UUID PRIMARY KEY,
    business_id     UUID NOT NULL REFERENCES business_profile(id),
    plan_id         UUID NULL REFERENCES execution_plans(id),
    snapshot_type   TEXT NOT NULL DEFAULT 'MANUAL',
    label           TEXT NULL,
    metrics_json    JSONB NOT NULL,
    insights_json   JSONB NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ps_business_id ON performance_snapshots(business_id);
CREATE INDEX IF NOT EXISTS idx_ps_plan_id ON performance_snapshots(plan_id);
CREATE INDEX IF NOT EXISTS idx_ps_snapshot_type ON performance_snapshots(snapshot_type);
CREATE INDEX IF NOT EXISTS idx_ps_created_at ON performance_snapshots(created_at);
