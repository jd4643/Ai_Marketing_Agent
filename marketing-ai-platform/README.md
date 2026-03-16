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
docker compose -f docker-compose.yml --env-file .env up --build5. Send request and confirm response contains the **original fields** (`requestId`, `strategyVersion`, `platformBudgetSplit`, `campaignPlan`, `funnelStrategy`, `expectedCPL`, `expectedROAS`, `reasoning`, `assumptions`) **plus new consultant-style playbook sections** (see [Consultant-Style Strategy Output](#consultant-style-strategy-output) below).


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

---

## Consultant-Style Strategy Output

### What changed (v2)
The `/strategy/generate` endpoint now returns a **full marketing playbook** instead of a flat budget template.

**Before (v1):** The response contained only `platformBudgetSplit`, `campaignPlan`, `funnelStrategy`, `expectedCPL`, `expectedROAS`, `reasoning`, and `assumptions`. This felt generic and template-like — a budget calculator, not a consultant.

**After (v2):** The response still contains all original fields (backward-compatible), but now includes **16 additional consultant-style sections** that make the strategy directly actionable:

| Section | Description |
|---|---|
| `businessSnapshot` | Business-specific summary using actual profile facts |
| `marketAnalysis` | Competition context, demand signals, buying behavior, seasonal factors |
| `customerPersona` | Primary buyer, motivations, pain points, purchase triggers |
| `whyThisStrategy` | Why chosen platforms fit THIS business; why others are deprioritized |
| `platformStrategy` | Per-platform: why chosen, objective, budget, duration, audience, creative format, success metric |
| `campaignArchitecture` | Campaign names/themes, ad group logic, keyword direction (Google), retargeting logic (Meta/TikTok) |
| `creativeStrategy` | Creative angles, hooks, messaging style, content types, examples |
| `creativesNeeded` | Exact assets to prepare: image/video/carousel/reel/testimonial with specs |
| `executionRoadmap` | Week 1-4 plan: what to launch, measure, and change |
| `setupChecklist` | Tracking, accounts, landing page, WhatsApp/contact, conversion events, Google Business Profile |
| `landingPageRecommendations` | Where to send traffic, required conversion elements |
| `offerStrategy` | Promotion, CTA, urgency tactic |
| `measurementPlan` | KPI targets, evaluation windows, stop-loss rules, scaling rules |
| `risksAndMitigations` | Likely problems and specific mitigation actions |
| `first14DaysLearningPlan` | Cold-start learning plan: data to collect, decisions to defer, testing plan |
| `humanReadablePlanMarkdown` | Full Markdown strategy document for UI rendering — feels like a human strategist wrote it |

### Why it behaves like a consultant, not a budget calculator
1. **Prompt engineering:** The LLM is instructed to behave as a "Senior Marketing Consultant and Growth Strategist" with strict anti-generic rules.
2. **Five structured context sections** are injected into the prompt separately (Business Memory, Performance Memory, Creative Winners, Trend Signals, Strategy Intelligence) — the LLM has rich context to personalize.
3. **Deterministic engine is source of truth:** The Strategy Intelligence Engine (decision tree, confidence scoring, pattern matching) selects the template. The LLM explains and elaborates — it cannot override the chosen template or invent fake performance data.
4. **Anti-generic validation** catches template-like output and retries or enriches deterministically.
5. **Rich fallback:** Even without OpenAI, the fallback builds all 16 playbook sections from the business profile and template data.

### How anti-generic detection works
After the LLM returns JSON, a validation layer checks:
- Business name is referenced somewhere in the output
- Industry is referenced somewhere in the output
- Platform choice explanation exists (`whyThisStrategy` or `platformStrategy`)
- `executionRoadmap` section exists
- `setupChecklist` section exists
- `creativeStrategy` section exists
- At least 4 of 16 consultant sections are present (not a flat budget template)

**If validation fails:**
1. **Retry once** with a stronger prompt that includes the specific violations.
2. If the retry also fails, **enrich** the LLM output with deterministic fallback sections (only fills missing sections, preserves what the LLM got right).

This approach is documented in `StrategyService.validateNotGeneric()` and `StrategyService.enrichWithFallbackSections()`.

### How cold-start users are handled
A "cold start" is detected when:
- No campaign metrics exist for the business (or all conversions = 0)
- No creative winners exist

Cold-start users receive:
- **Conservative budget allocation** and realistic expectations (ranges, not point estimates)
- **`first14DaysLearningPlan`** with data to collect, decisions to defer, and structured testing plan
- **Prompt instructions** that explicitly tell the LLM: "No historical data exists — focus on learning, not sales targets"
- **Markdown plan** that clearly flags the learning phase

As the business accumulates performance data and creative winners, subsequent strategy generations become increasingly personalized and data-driven.

### How strategy becomes smarter over time
1. **Performance memory:** Each generation uses the last 30 days of campaign metrics (spend, ROAS, conversions by platform).
2. **Creative winners:** Top 3 performing creatives are injected — the LLM can reference winning hooks and angles.
3. **Pattern matching:** If a previous strategy run with similar parameters was successful (ROAS ≥ 2.0 or conversions ≥ 10), the proven template is reused.
4. **Trend signals:** The latest 7-day trends for the business's industry are injected.
5. **Confidence scoring:** The deterministic confidence score (0–100) adjusts based on data availability — cold starts score lower, data-rich businesses score higher.

### Sample request
```bash
curl -X POST localhost:8080/strategy/generate \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: 550e8400-e29b-41d4-a716-446655440000' \
  -d '{
    "businessId": "11111111-1111-1111-1111-111111111111",
    "objective": "sales",
    "monthlyBudget": 2000,
    "trends": ["minimalist jewelry"],
    "notes": "focus DTC"
  }'
```

### Sample response (abbreviated)
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "strategyVersion": "v2",
  "platformBudgetSplit": { "meta": 1000, "google": 600, "tiktok": 400, "youtube": 0 },
  "campaignPlan": [
    { "platform": "meta", "dailyBudget": 33.33, "objective": "conversions", "targeting": "women 25-45 interested in jewelry", "creativeHook": "Discover Acme Jewelry — handcrafted rings starting at $50" }
  ],
  "funnelStrategy": "Top-of-funnel awareness on Meta → retargeting engaged users → Google search capture for high-intent buyers",
  "expectedCPL": "$8-$15 range (conservative, pending learning phase data)",
  "expectedROAS": "1.5x-2.5x after 30 days of optimization",
  "reasoning": "Acme Jewelry sells rings at $50-$200 targeting women 25-45. Meta is the primary channel for visual product discovery...",
  "assumptions": ["No prior ad history — estimates are conservative", "Website is conversion-ready"],

  "businessSnapshot": {
    "summary": "Acme Jewelry is an online jewelry business selling rings at $50-$200, targeting women 25-45 via https://acme.example.com."
  },
  "marketAnalysis": {
    "competitionContext": "The online jewelry market is highly competitive with both DTC brands and marketplaces...",
    "demandContext": "Minimalist jewelry is trending based on recent signals...",
    "buyingBehavior": "Jewelry purchases are often emotional, gift-driven, or self-reward...",
    "seasonalConsiderations": "Valentine's Day, Mother's Day, and holiday season drive peak demand..."
  },
  "customerPersona": {
    "primaryBuyer": "Women aged 25-45, urban, mid-income, fashion-conscious",
    "motivations": "Self-expression, gifting, treating themselves",
    "painPoints": "Trust in online jewelry quality, sizing uncertainty",
    "triggers": "Sales events, influencer endorsements, friend recommendations"
  },
  "executionRoadmap": {
    "week1": "Set up Meta Pixel on acme.example.com, create Business Manager, launch 3 awareness ad sets...",
    "week2": "Review CTR and CPM data, pause underperforming creatives, test 2 new hooks...",
    "week3": "Launch retargeting campaign for website visitors, narrow audiences...",
    "week4": "Full performance review, scale winning ad sets by 20%, plan month 2...",
    "launchFirst": "Meta awareness campaign with product hero images",
    "measureFirst": "CTR, CPM, link clicks in first 72 hours",
    "changeAfter": "Pause creatives with CTR < 0.8% after 1000 impressions"
  },
  "setupChecklist": [
    { "item": "Meta Pixel", "details": "Install on acme.example.com", "priority": "HIGH" },
    { "item": "Conversion Events", "details": "Configure Purchase and AddToCart events", "priority": "HIGH" }
  ],
  "creativeStrategy": {
    "creativeAngles": ["Minimalist elegance", "Gift-worthy", "Handcrafted quality"],
    "hooks": ["Discover rings you won't find anywhere else", "The perfect gift under $200"],
    "messagingStyle": "Emotional, aspirational, benefit-focused",
    "contentTypes": ["Product hero images", "Short-form video (15s)", "Carousel of collections"],
    "examples": ["Close-up of ring on hand with natural lighting", "Unboxing reel with customer reaction"]
  },
  "humanReadablePlanMarkdown": "# Marketing Strategy for Acme Jewelry\n\n## Business Overview\nAcme Jewelry sells handcrafted rings...\n\n## Week-by-Week Plan\n- **Week 1:** ...",

  "first14DaysLearningPlan": null,
  "risksAndMitigations": [
    { "risk": "Low initial conversion rate due to cold audience", "mitigation": "Focus weeks 1-2 on engagement, not sales..." }
  ],
  "measurementPlan": {
    "kpiTargets": { "CTR": "> 1.0%", "CPC": "< $1.50", "ROAS": "> 2.0x after learning" },
    "evaluationWindow": "7-day rolling, full review at day 14 and 30",
    "stopLossRules": ["Pause ad set if spend > 3x CPA with 0 conversions"],
    "scalingRules": ["Increase 20% on ad sets with ROAS > 2.0x for 3+ days"]
  }
}
```

### Testing the consultant-style output
The following test classes cover the upgrade:

| Test class | What it covers |
|---|---|
| `PromptBuilderTest` | Consultant role injection, business facts, anti-generic rules, cold-start instructions, template intelligence fields |
| `PromptBuilderInjectionTest` | All 5 context sections (Business, Performance, Creative, Trends, Intelligence) are present |
| `ConsultantStrategyTest.AntiGenericValidation` | Rejects missing business name, missing industry, flat budget templates, missing sections |
| `ConsultantStrategyTest.RichFallback` | Fallback contains executionRoadmap, setupChecklist, creativeStrategy, markdown, measurementPlan, risks |
| `ConsultantStrategyTest.ColdStart` | first14DaysLearningPlan present, markdown mentions learning phase, conservative prompt language |
| `ConsultantStrategyTest.BackwardCompatibility` | Original required fields still present, legacy normalize still works |

Run tests:
```bash
cd strategy-service
mvn test -pl .
```

