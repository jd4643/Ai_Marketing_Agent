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


## Windows PowerShell quick start (no `head`, no local `psql` needed)
If you are on PowerShell, use these commands instead of Unix pipeline examples:

```powershell
# 1) Find the postgres container id
$pg = docker ps --filter "name=postgres" --format "{{.ID}}" | Select-Object -First 1

# 2) Insert sample business profile using psql inside the container
docker exec -it $pg psql -U postgres -d marketing_ai -c "INSERT INTO business_profile(id,business_name,industry,created_at,updated_at) VALUES ('11111111-1111-1111-1111-111111111111','Acme Jewelry','jewelry',NOW(),NOW()) ON CONFLICT (id) DO NOTHING;"

# 3) Strategy generate via gateway
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/strategy/generate" -Headers @{"Content-Type"="application/json";"X-Request-Id"="550e8400-e29b-41d4-a716-446655440000"} -Body '{"businessId":"11111111-1111-1111-1111-111111111111","objective":"sales","monthlyBudget":2000,"trends":["minimalist jewelry"],"notes":"focus DTC"}'

# 4) Strategy history
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/strategy/history?businessId=11111111-1111-1111-1111-111111111111&limit=20"
```

### Why your previous command failed
- `head` is a Unix tool, not a default PowerShell command. Use `Select-Object -First 1`.
- `psql` is not required on Windows host; run `psql` inside the running postgres container with `docker exec`.
- `-c` belongs to `psql`, so it must be part of the same `docker exec ... psql ... -c "SQL"` command.

## Postman steps for strategy-service
1. Method `POST`, URL `http://localhost:8080/strategy/generate`.
2. Header `Content-Type: application/json`.
3. Header `X-Request-Id: <uuid>` (example: `550e8400-e29b-41d4-a716-446655440000`).
4. Body raw JSON:
```json
{
  "businessId":"11111111-1111-1111-1111-111111111111",
  "objective":"sales",
  "monthlyBudget":2000,
  "trends":["minimalist jewelry"],
  "notes":"focus DTC"
}
```
5. Send request and confirm response contains `requestId`, `strategyVersion`, `platformBudgetSplit`, `campaignPlan`, `funnelStrategy`, `expectedCPL`, `expectedROAS`, `reasoning`, `assumptions`.


## Strategy Intelligence Engine
`strategy-service` now runs a deterministic intelligence layer before LLM generation:
- Decision tree picks a strategy template key from `strategy_template` using online/offline motion, price tier, budget tier, and objective.
- Confidence scorer outputs 0–100 with weighted breakdown (`margin_strength`, `competition_density`, `offer_strength`, `market_demand`, `channel_fit`, `trust_level`).
- Pattern matcher checks prior successful runs and can reuse a proven template when similarity exceeds threshold.
- Every run persists intelligence artifacts in `strategy_run_intel` for feedback loop learning.

Environment knobs:
- `STRATEGY_SIMILARITY_THRESHOLD` (default `0.80`)
- `STRATEGY_SUCCESS_MIN_ROAS` (default `2.0`)
- `STRATEGY_SUCCESS_MIN_CONVERSIONS` (default `10`)

To inspect stored intelligence:
```bash
curl 'localhost:8080/strategy/intel/history?businessId=11111111-1111-1111-1111-111111111111&limit=20'
```

## New User Onboarding
New users **must create a business profile** before calling `/strategy/generate`. The onboarding endpoints live in `strategy-service` under the `/strategy/business-profiles` prefix.

### 1) Create a business profile
```bash
curl -X POST localhost:8080/strategy/business-profiles \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: 550e8400-e29b-41d4-a716-446655440000' \
  -d '{
    "businessName": "Acme Jewelry",
    "industry": "jewelry",
    "product": "rings",
    "priceRange": "$50-$200",
    "location": "US",
    "targetAudience": "women 25-45",
    "websiteUrl": "https://acme.example.com"
  }'
```
Response (`201 Created`):
```json
{
  "businessId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "createdAt": "2026-03-03T12:00:00Z"
}
```

### 2) Generate strategy using the returned businessId
```bash
curl -X POST localhost:8080/strategy/generate \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: 660e8400-e29b-41d4-a716-446655440001' \
  -d '{
    "businessId": "<businessId from step 1>",
    "objective": "sales",
    "monthlyBudget": 2000,
    "trends": ["minimalist jewelry"],
    "notes": "focus DTC"
  }'
```

### 3) Retrieve a business profile
```bash
curl localhost:8080/strategy/business-profiles/<businessId> \
  -H 'X-Request-Id: 770e8400-e29b-41d4-a716-446655440002'
```

### 4) Update a business profile
```bash
curl -X PUT localhost:8080/strategy/business-profiles/<businessId> \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: 880e8400-e29b-41d4-a716-446655440003' \
  -d '{
    "businessName": "Acme Fine Jewelry",
    "industry": "jewelry",
    "product": "rings and bracelets",
    "priceRange": "$100-$500",
    "location": "US",
    "targetAudience": "women 25-55",
    "websiteUrl": "https://acmefine.example.com"
  }'
```

#### Validation
- `businessName` and `industry` are required (non-blank).
- `websiteUrl` may be null or blank.
- Invalid requests return `400` with the standard `ApiError` JSON including `requestId`.
- Fetching or updating a non-existent profile returns `404`.

## API examples
Create business profile (onboarding — see above):
```bash
curl -X POST localhost:8080/strategy/business-profiles -H 'Content-Type: application/json' -H 'X-Request-Id: 550e8400-e29b-41d4-a716-446655440000' -d '{"businessName":"Acme Jewelry","industry":"jewelry"}'
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
