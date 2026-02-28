# marketing-ai-platform

Production-oriented monorepo implementing a multi-service marketing AI platform with persistent memory and feedback loops.

## What makes this different than ChatGPT
- Stores **business profile memory** in `business_profile`.
- Stores **strategy generation history** in `strategy_history`.
- Stores **performance telemetry** in `campaign_metrics`.
- Stores **creative winners** in `creatives`.
- Stores **fresh trend signals** in `trends` every 6 hours.
- Strategy prompt injection uses profile + last-30-day metrics + winning creatives.
- Creative prompt injection uses profile + last-7-day trends + winning creatives + optional linked strategy.

## Services
- `api-gateway` (8080): routing, request-id propagation, CORS, per-IP rate limit.
- `strategy-service` (8081): strategy generation, deterministic rules, OpenAI JSON response + fallback, history persistence.
- `creative-service` (8082): creative blueprints using trends/strategy/winners.
- `analytics-service` (8083): metrics ingest + summary; owns Flyway migration.
- `trend-service` (8091): pytrends ingestion, scheduled refresh, trends read API.
- `generation-service` (8092): production-safe image generation stub.

## Environment
Copy `infra/.env.example` -> `infra/.env` and update secrets.

Required variables:
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `PY_DB_DSN`
- `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_TIMEOUT_SECONDS`
- `GATEWAY_RATE_LIMIT_PER_MINUTE`

## Run
```bash
chmod +x setup.sh
./setup.sh
```

Or directly:
```bash
cp infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml --env-file infra/.env up --build
```

## API examples
Create sample business profile:
```bash
docker exec -it $(docker ps --filter name=postgres --format '{{.ID}}' | head -n1) psql -U postgres -d marketing_ai -c "INSERT INTO business_profile(id,business_name,industry,created_at,updated_at) VALUES ('11111111-1111-1111-1111-111111111111','Acme Jewelry','jewelry',NOW(),NOW());"
```

Generate strategy:
```bash
curl -X POST localhost:8080/strategy/generate -H 'Content-Type: application/json' -d '{"businessId":"11111111-1111-1111-1111-111111111111","objective":"sales","monthlyBudget":2000,"trends":["minimalist jewelry"],"notes":"focus DTC"}'
```

Generate creative:
```bash
curl -X POST localhost:8080/creative/generate -H 'Content-Type: application/json' -d '{"businessId":"11111111-1111-1111-1111-111111111111","platform":"meta","format":"image","objective":"sales"}'
```

Ingest analytics metric:
```bash
curl -X POST localhost:8080/analytics/metrics/ingest -H 'Content-Type: application/json' -d '{"businessId":"11111111-1111-1111-1111-111111111111","platform":"meta","spend":125.5,"impressions":10000,"clicks":220,"conversions":12,"revenue":620,"ctr":0.022,"cpc":0.57,"cpa":10.45,"roas":4.94,"recordedAt":"2026-01-01T12:00:00Z"}'
```

Latest trends:
```bash
curl 'localhost:8080/trends/latest?industry=jewelry&days=7&limit=20'
```

Generation stub:
```bash
curl -X POST localhost:8080/generate/image -H 'Content-Type: application/json' -d '{"businessId":"11111111-1111-1111-1111-111111111111","prompt":"clean product hero on marble","size":"1024x1024"}'
```

## Notes on external credentials
- `strategy-service` and `creative-service` require `OPENAI_API_KEY` for live LLM responses.
- If key is missing/invalid, deterministic fallback/stub JSON responses are returned and history is recorded.
- `generation-service` intentionally returns `STUBBED` until `OPENAI_IMAGE_API_KEY` provider integration is added.
