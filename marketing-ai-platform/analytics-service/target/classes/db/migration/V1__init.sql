CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE IF NOT EXISTS business_profile (
 id UUID PRIMARY KEY,
 business_name TEXT NOT NULL,
 industry TEXT NOT NULL,
 product TEXT,
 price_range TEXT,
 location TEXT,
 target_audience TEXT,
 website_url TEXT,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS campaign_metrics (
 id UUID PRIMARY KEY,
 business_id UUID NOT NULL REFERENCES business_profile(id),
 platform TEXT NOT NULL,
 spend NUMERIC NOT NULL,
 impressions BIGINT,
 clicks BIGINT,
 conversions BIGINT,
 revenue NUMERIC,
 ctr NUMERIC,
 cpc NUMERIC,
 cpa NUMERIC,
 roas NUMERIC,
 recorded_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS creatives (
 id UUID PRIMARY KEY,
 business_id UUID NOT NULL REFERENCES business_profile(id),
 platform TEXT NOT NULL,
 format TEXT,
 angle TEXT,
 hook TEXT,
 performance_score NUMERIC,
 created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS strategy_history (
 id UUID PRIMARY KEY,
 request_id UUID NOT NULL,
 business_id UUID NOT NULL REFERENCES business_profile(id),
 objective TEXT NOT NULL,
 monthly_budget NUMERIC NOT NULL,
 trends_json JSONB,
 prompt_version TEXT NOT NULL,
 model_name TEXT NOT NULL,
 request_json JSONB NOT NULL,
 response_json JSONB,
 status TEXT NOT NULL,
 error_code TEXT,
 error_message TEXT,
 openai_latency_ms BIGINT,
 created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS trends (
 id UUID PRIMARY KEY,
 keyword TEXT NOT NULL,
 source TEXT NOT NULL,
 industry TEXT,
 geo TEXT,
 trend_score NUMERIC,
 captured_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_trends_captured_at ON trends(captured_at);
CREATE INDEX IF NOT EXISTS idx_trends_industry_captured_at ON trends(industry, captured_at);
