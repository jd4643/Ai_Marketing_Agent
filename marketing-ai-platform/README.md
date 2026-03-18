# marketing-ai-platform

Production-oriented monorepo implementing a multi-service marketing AI platform with persistent memory and feedback loops.

## What makes this different than ChatGPT
- Stores **business profile memory** in `business_profile`.
- Stores **strategy generation history** in `strategy_history`.
- Stores **performance telemetry** in `campaign_metrics`.
- Stores **creative winners** in `creatives`.
- Stores **generated creative assets** in `creative_assets` (linked to business + strategy + creative concept).
- Stores **fresh trend signals** in `trends` every 6 hours.
- Strategy prompt injection uses profile + last-30-day metrics + winning creatives.
- Creative prompt injection uses profile + last-7-day trends + winning creatives + optional linked strategy.
- Creative blueprints are generation-ready: each concept includes AI image/video prompts and generation metadata.
- Asset generation persists records with full provenance for feedback loop learning.

## Services
- `api-gateway` (8080): routing, request-id propagation, CORS, per-IP rate limit.
- `strategy-service` (8081): strategy generation, deterministic rules, OpenAI JSON response + fallback, history persistence.
- `creative-service` (8082): creative blueprints using trends/strategy/winners.
- `analytics-service` (8083): metrics ingest + summary; owns Flyway migration; Meta Ads API integration (read-only sync, asset mapping, derived performance).
- `trend-service` (8091): pytrends ingestion, scheduled refresh, trends read API.
- `generation-service` (8092): creative asset generation with DB persistence, prompt builder, DALL-E integration (stubbed by default).

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
- `generation-service` returns `STUBBED` status by default. Set `OPENAI_IMAGE_API_KEY` for real DALL-E image generation.
- `OPENAI_IMAGE_MODEL` defaults to `dall-e-3`. `OPENAI_IMAGE_BASE_URL` defaults to the standard OpenAI endpoint.

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

---

## Creative Asset Generation

### End-to-End User Flow
The platform now supports a complete creative production pipeline:

```
Create Business → Generate Strategy → Generate Creative Blueprint → Generate Assets
```

1. **Create business profile** via `POST /strategy/business-profiles`
2. **Generate marketing strategy** via `POST /strategy/generate` → returns `requestId`
3. **Generate creative blueprints** via `POST /creative/generate` (pass `strategyRequestId` to link)
4. **Generate creative assets** via `POST /generate/creative-assets` (use blueprint fields as input)
5. **Retrieve assets** via `GET /generate/assets?businessId=...`

Each creative concept in the blueprint response now includes:
- `generationReady: true` — indicates the concept can be sent directly to generation
- `recommendedAssetTypes` — e.g. `["image", "video", "carousel"]`
- `generationPayloadExample` — a ready-to-use JSON payload for `POST /generate/creative-assets`

### Creative Blueprint Fields (enriched)
Each concept in `POST /creative/generate` response now includes:

| Field | Description |
|---|---|
| `conceptName` | Unique creative concept name |
| `platform` | Target platform |
| `format` | Ad format |
| `hook` | Attention-grabbing opening line |
| `emotionalAngle` | Core emotional trigger |
| `visualStyle` | cinematic, minimal, bold-graphic, UGC, lifestyle, editorial |
| `productFocus` | Product/service element highlighted |
| `sceneDescription` | Detailed visual scene layout |
| `compositionNotes` | Framing, focal point, layout |
| `lightingNotes` | Lighting style |
| `brandTone` | Tone of voice for copy |
| `primaryText` | Main ad copy |
| `headline` | Short headline |
| `cta` | Call to action |
| `aiImagePrompt` | Complete DALL-E/Midjourney-ready image prompt |
| `aiVideoPrompt` | Complete video ad concept description |
| `performanceAngle` | Marketing psychology angle |
| `trendUsed` | Which trend influenced this concept |
| `rationale` | Why this concept will perform |
| `recommendedAssetTypes` | Suggested asset types to generate |
| `generationReady` | Boolean: true if ready for asset generation |
| `generationPayloadExample` | Example payload for `/generate/creative-assets` |

All new fields are additive. Existing fields remain unchanged for backward compatibility.

### Generate Creative Assets
**Endpoint:** `POST /generate/creative-assets`

**Request:**
```json
{
  "businessId": "UUID (required)",
  "creativeId": "UUID (optional — links to creatives table)",
  "strategyRequestId": "UUID (optional — links to strategy run)",
  "assetType": "image|video|carousel (required)",
  "platform": "meta|google|tiktok|youtube (optional)",
  "prompt": "string (optional — direct prompt override)",
  "creativeConceptName": "string (optional)",
  "count": 1,
  "size": "1024x1024 (optional)",
  "trendContext": {
    "industry": "optional",
    "keywords": ["optional"]
  },
  "metadata": {
    "hook": "optional",
    "headline": "optional",
    "cta": "optional",
    "visualStyle": "optional",
    "emotionalAngle": "optional",
    "sceneDescription": "optional",
    "compositionNotes": "optional",
    "lightingNotes": "optional",
    "brandTone": "optional",
    "productFocus": "optional",
    "conceptName": "optional"
  }
}
```

**Response:**
```json
{
  "requestId": "UUID",
  "status": "SUCCESS|STUBBED|FAILED",
  "assets": [
    {
      "assetId": "UUID",
      "assetType": "image",
      "status": "SUCCESS|STUBBED|FAILED",
      "url": "string or null",
      "thumbnailUrl": "string or null",
      "provider": "OPENAI|STUB",
      "providerAssetId": "string or null",
      "promptUsed": "the final generated prompt"
    }
  ]
}
```

### Retrieve Generated Assets
```bash
# List assets for a business
curl 'localhost:8080/generate/assets?businessId=<UUID>&limit=20'

# Get single asset
curl 'localhost:8080/generate/assets/<assetId>'
```

### Stubbed vs Real Generation
- **Without `OPENAI_IMAGE_API_KEY`:** Returns `STUBBED` status. Records are persisted to DB with full metadata. The response structure is identical to real generation — frontend code does not need to change.
- **With `OPENAI_IMAGE_API_KEY`:** Calls OpenAI DALL-E API for real image generation. Returns `SUCCESS` status with actual asset URLs.

### Required Environment Variables for Real Generation
```bash
# Add to infra/.env for real image generation:
OPENAI_IMAGE_API_KEY=sk-your-openai-key
OPENAI_IMAGE_MODEL=dall-e-3          # default: dall-e-3
OPENAI_IMAGE_BASE_URL=https://api.openai.com/v1/images/generations  # default
```

### Prompt Builder
The generation service includes a `CreativeAssetPromptBuilder` that constructs rich, visually descriptive prompts from:
- Creative blueprint fields (scene, style, lighting, composition, mood)
- Business context (industry, product)
- Trend context (keywords injected subtly)
- Platform considerations (aspect ratio, format hints)

If a direct `prompt` field is provided without metadata, it passes through unchanged. Otherwise, the builder composes a structured prompt optimized for image/video generation.

### Database: creative_assets Table
All generated assets are persisted in `creative_assets` (Flyway migration V3):

| Column | Type | Description |
|---|---|---|
| `id` | UUID PK | Asset identifier |
| `business_id` | UUID FK | Links to business_profile |
| `creative_id` | UUID FK nullable | Links to creatives table |
| `strategy_request_id` | UUID nullable | Links to strategy run |
| `asset_type` | TEXT | IMAGE, VIDEO, CAROUSEL |
| `platform` | TEXT nullable | meta, google, tiktok, youtube |
| `prompt_text` | TEXT | Final prompt used for generation |
| `provider` | TEXT | STUB, OPENAI |
| `provider_asset_id` | TEXT nullable | Provider's asset identifier |
| `asset_url` | TEXT nullable | Generated asset URL |
| `thumbnail_url` | TEXT nullable | Thumbnail URL |
| `status` | TEXT | PENDING, SUCCESS, FAILED, STUBBED |
| `trend_context_json` | JSONB nullable | Trend context preserved |
| `metadata_json` | JSONB nullable | Creative metadata preserved |
| `created_at` | TIMESTAMP | Auto-set |

Indexes on `business_id`, `strategy_request_id`, and `created_at`.

### Full E2E Example with curl

```bash
# Step 1: Create business profile
curl -X POST localhost:8080/strategy/business-profiles \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: aaa00000-0000-0000-0000-000000000001' \
  -d '{
    "businessName": "Acme Jewelry",
    "industry": "jewelry",
    "product": "gold rings",
    "priceRange": "$50-$200",
    "location": "US",
    "targetAudience": "women 25-45",
    "websiteUrl": "https://acme.example.com"
  }'
# Save the returned businessId

# Step 2: Generate strategy
curl -X POST localhost:8080/strategy/generate \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: aaa00000-0000-0000-0000-000000000002' \
  -d '{
    "businessId": "<businessId>",
    "objective": "sales",
    "monthlyBudget": 2000,
    "trends": ["minimalist jewelry"],
    "notes": "focus DTC"
  }'
# Save the returned requestId as strategyRequestId

# Step 3: Generate creative blueprint
curl -X POST localhost:8080/creative/generate \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: aaa00000-0000-0000-0000-000000000003' \
  -d '{
    "businessId": "<businessId>",
    "platform": "meta",
    "format": "image",
    "objective": "sales",
    "strategyRequestId": "<strategyRequestId>"
  }'
# Response includes creativeConcepts with generationReady=true
# Use the generationPayloadExample from any concept as input to step 4

# Step 4: Generate creative asset (image)
curl -X POST localhost:8080/generate/creative-assets \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: aaa00000-0000-0000-0000-000000000004' \
  -d '{
    "businessId": "<businessId>",
    "strategyRequestId": "<strategyRequestId>",
    "assetType": "image",
    "platform": "meta",
    "creativeConceptName": "Trend-led UGC for Acme Jewelry",
    "metadata": {
      "hook": "Stop scrolling — Acme Jewelry just changed the game",
      "headline": "Make the switch today",
      "cta": "Shop Now",
      "visualStyle": "UGC",
      "sceneDescription": "Close-up of gold rings being unboxed in a lifestyle setting",
      "lightingNotes": "Natural daylight, warm tones",
      "productFocus": "gold rings",
      "emotionalAngle": "curiosity and social proof"
    },
    "trendContext": {
      "industry": "jewelry",
      "keywords": ["minimalist jewelry", "gold necklace"]
    }
  }'

# Step 5: List generated assets
curl 'localhost:8080/generate/assets?businessId=<businessId>&limit=20'

# Step 6: Get single asset details
curl 'localhost:8080/generate/assets/<assetId>'
```

### Feedback Loop Readiness
Generated asset records preserve:
- Business linkage (`business_id`)
- Strategy linkage (`strategy_request_id`)
- Creative concept linkage (`creative_id`)
- Full prompt text used for generation
- Trend context at time of generation
- Creative metadata (hook, headline, CTA, concept name, etc.)

This enables future performance tracking per asset, allowing the platform to learn which creative concepts, prompts, and trend directions produce the best-performing ads.

---

## Creative Performance Intelligence (Phase 3)

### Overview
Phase 3 closes the feedback loop: the platform now **tracks performance at the individual creative asset level**, automatically **detects winners**, and **feeds winning signals back** into both strategy and creative generation. This creates a self-improving cycle:

```
Generate Assets → Run Ads → Ingest Performance → Detect Winners → Generate Smarter Assets
```

### How Winner Detection Works
Each creative asset is classified into one of four categories based on configurable thresholds:

| Classification | Criteria |
|---|---|
| **WINNER** | ≥ 3000 impressions AND ≥ 100 clicks AND ≥ 3 conversions AND ≥ 2.0 ROAS |
| **TESTING** | ≥ 3000 impressions but does not meet all WINNER thresholds |
| **WEAK** | ≥ 3000 impressions AND ROAS ≤ 0.8 |
| **INSUFFICIENT_DATA** | < 3000 impressions (not enough data to classify) |

Thresholds are configurable via environment variables (see below).

### New Endpoints

#### 1. Ingest Asset-Level Performance
```bash
curl -X POST localhost:8080/analytics/creative-assets/metrics/ingest \
  -H 'Content-Type: application/json' \
  -d '{
    "creativeAssetId": "<UUID from /generate/creative-assets>",
    "businessId": "<businessId>",
    "platform": "meta",
    "impressions": 5000,
    "clicks": 220,
    "conversions": 12,
    "spend": 125.50,
    "revenue": 620.00,
    "ctr": 0.044,
    "cpc": 0.57,
    "cpa": 10.46,
    "roas": 4.94,
    "recordedAt": "2026-01-15T12:00:00Z"
  }'
```
Response: `201 Created` with the saved performance record.

#### 2. Asset Performance Summary
```bash
curl 'localhost:8080/analytics/creative-assets/summary?businessId=<UUID>&days=30'
```
Returns aggregated metrics grouped by asset and platform (total impressions, clicks, conversions, spend, revenue, average CTR/CPC/CPA/ROAS).

#### 3. Winner Detection
```bash
curl 'localhost:8080/analytics/creative-assets/winners?businessId=<UUID>&days=30&limit=10'
```
Returns classified assets with their status (`WINNER`, `TESTING`, `WEAK`, `INSUFFICIENT_DATA`), metrics, and the thresholds used for classification.

#### 4. Performance Insights
```bash
curl 'localhost:8080/analytics/creative-assets/insights?businessId=<UUID>&days=30'
```
Returns an insights report including:
- Count breakdown by classification (winners, testing, weak, insufficient)
- Best asset ID and its ROAS
- Overall ROAS and total spend/revenue
- Actionable recommendations array (e.g., "Scale budget on 3 winning assets", "Pause 2 weak assets with ROAS ≤ 0.8")

#### 5. Generate Variation from Winner
```bash
curl -X POST localhost:8080/generate/creative-assets/from-winner \
  -H 'Content-Type: application/json' \
  -d '{
    "winnerAssetId": "<UUID of a winning asset>",
    "businessId": "<businessId>",
    "variationType": "iteration",
    "assetType": "image",
    "platform": "meta",
    "count": 3,
    "size": "1024x1024"
  }'
```

**Variation types:**
| Type | Behavior |
|---|---|
| `iteration` | Keeps winning elements (hook, style, angle) and iterates on them |
| `remix` | Creates a fresh interpretation using winning signals as inspiration |
| `opposite` | Tests a contrasting approach (e.g., UGC → editorial, minimal → bold-graphic) |

Response includes `winner_provenance` with the source asset ID, variation type, and original winning metrics.

### Winner-Aware Generation
Once assets have performance data, the platform automatically uses winning signals:

**Creative Service (`POST /creative/generate`):**
- Queries asset winners (≥ 3000 impressions, ≥ 2.0 ROAS)
- Injects winning hooks, visual styles, and emotional angles into the creative director prompt
- Response includes `basedOnWinners: true` and `winnerSignalsUsed: N` when winners influenced generation

**Strategy Service (`POST /strategy/generate`):**
- Queries asset winners and injects them into the performance summary
- The intelligence prompt includes a "CREATIVE ASSET WINNERS" section (C2) with performance-proven signals
- Response includes `winnerInsights` array (per-winner breakdown) and `recommendedNextCreativeMoves` (actionable next steps)

### Database: creative_asset_performance Table
Flyway migration V4 creates the performance tracking table:

| Column | Type | Description |
|---|---|---|
| `id` | UUID PK | Performance record identifier |
| `creative_asset_id` | UUID FK | Links to creative_assets |
| `business_id` | UUID FK | Links to business_profile |
| `platform` | TEXT | Platform (meta, google, tiktok, youtube) |
| `impressions` | BIGINT | Total impressions |
| `clicks` | BIGINT | Total clicks |
| `conversions` | BIGINT | Total conversions |
| `spend` | NUMERIC | Total spend |
| `revenue` | NUMERIC | Total revenue |
| `ctr` | NUMERIC | Click-through rate |
| `cpc` | NUMERIC | Cost per click |
| `cpa` | NUMERIC | Cost per acquisition |
| `roas` | NUMERIC | Return on ad spend |
| `recorded_at` | TIMESTAMP | When the metric was recorded |
| `created_at` | TIMESTAMP | Auto-set insertion time |

Indexes on `creative_asset_id`, `business_id`, `platform`, and `recorded_at`.

### Environment Variables (Phase 3)
Add to `infra/.env`:
```bash
CREATIVE_WINNER_MIN_IMPRESSIONS=3000
CREATIVE_WINNER_MIN_CLICKS=100
CREATIVE_WINNER_MIN_CONVERSIONS=3
CREATIVE_WINNER_MIN_ROAS=2.0
CREATIVE_WEAK_MAX_ROAS=0.8
```

### The Learning Loop in Practice

**Cold start (no performance data):**
- Strategy and creative generation work normally using business profile, trends, and intelligence engine
- All generated assets are persisted with full metadata for future tracking

**After running ads and ingesting metrics:**
1. Call `POST /analytics/creative-assets/metrics/ingest` with ad platform data (daily or on-demand)
2. Check `GET /analytics/creative-assets/winners` to see which assets are performing
3. Subsequent `POST /strategy/generate` calls automatically incorporate winner signals
4. Subsequent `POST /creative/generate` calls reference winning hooks and styles
5. Use `POST /generate/creative-assets/from-winner` to create variations of proven winners

**Over time, the platform:**
- Identifies which hooks, visual styles, and emotional angles work for each business
- Generates increasingly targeted creative concepts based on proven winners
- Provides data-driven strategy recommendations that reference actual creative performance
- Reduces creative waste by scaling winners and pausing weak assets

### Testing Phase 3
```bash
# Analytics service (5 new tests + 2 existing)
cd analytics-service && mvn test

# Generation service (8 new tests + 18 existing)
cd generation-service && python -m pytest test_app.py -v

# Creative service (regression — no new tests needed)
cd creative-service && mvn test

# Strategy service (3 new integration tests)
cd strategy-service && mvn test -Dtest=AssetWinnerIntegrationTest
```

---

## Meta Ads API Integration (Phase 4)

### Overview
Phase 4 adds the first **real ad-platform integration**: Meta Ads API (read-only). The platform now connects to live Meta ad accounts, syncs ad metadata and performance insights, maps external ads to internal creative assets, and feeds real performance data into the existing winner detection loop.

This is a **read-only** integration — no campaign creation, edits, publishing, or budget changes on Meta.

```
Connect Meta Account → Sync Ads + Insights → Map to Internal Assets → Derive Performance → Feed Learning Loop
```

### Architecture
- **Platform abstraction layer**: Interfaces (`AdPlatformSyncClient`, `AdPlatformInsightNormalizer`, `AdPlatformAssetMapper`) enable future Google/TikTok integrations
- **Token security**: AES-256-GCM encryption at rest via `PLATFORM_TOKEN_ENCRYPTION_KEY` (optional for local dev)
- **Meta Marketing API v21.0**: Configurable API version and base URL
- **Retry + backoff**: Exponential backoff with jitter (1s base, 3 max retries) on 429/5xx
- **Scheduled sync**: Background sync every hour for all active connections (configurable)
- **Automatic performance bridging**: `derivePerformance()` writes to `creative_asset_performance`, feeding the existing winner detection in both strategy-service and creative-service

### New Endpoints

All endpoints are routed through the API gateway at `/analytics/integrations/meta/*`.

#### 1. Connect Meta Ad Account
```bash
curl -X POST localhost:8080/analytics/integrations/meta/connect \
  -H 'Content-Type: application/json' \
  -d '{
    "businessId": "<UUID>",
    "metaAdAccountId": "act_123456789",
    "connectionName": "Main Meta Account",
    "accessToken": "<META_ACCESS_TOKEN>",
    "metaBusinessId": "optional_meta_biz_id"
  }'
```
Response (`201 Created`):
```json
{
  "requestId": "...",
  "connectionId": "UUID",
  "platform": "META",
  "status": "ACTIVE",
  "externalAccountId": "act_123456789"
}
```

#### 2. List Connections
```bash
curl 'localhost:8080/analytics/integrations/meta?businessId=<UUID>'
```

#### 3. Disconnect
```bash
curl -X POST localhost:8080/analytics/integrations/meta/<connectionId>/disconnect
```

#### 4. Trigger Sync
```bash
curl -X POST localhost:8080/analytics/integrations/meta/<connectionId>/sync
```
Returns a summary: ads synced, insights synced, assets mapped, performance records derived.

#### 5. Sync Status
```bash
curl 'localhost:8080/analytics/integrations/meta/<connectionId>/sync-status'
```
Returns: connection status, last synced timestamp, ad count, mapped asset count.

#### 6. Insights Summary
```bash
curl 'localhost:8080/analytics/integrations/meta/<connectionId>/insights?days=30'
```
Returns: aggregated spend/impressions/clicks/conversions/revenue, top campaigns, top ads by ROAS, mapped vs unmapped count, winning hooks, and recommendations.

### How It Feeds the Learning Loop
The `sync` operation runs this pipeline:
1. **syncAds**: Fetches all ads from Meta, upserts into `ad_platform_ads`
2. **syncInsights**: Fetches daily insights, normalizes conversions/revenue, stores in `ad_platform_insights`
3. **mapCreativeAssets**: Maps external Meta ads to internal `creative_assets` using 3-tier matching (metadata match → name match → creative metadata similarity)
4. **derivePerformance**: For each mapped asset, aggregates Meta insights and writes to `creative_asset_performance`

Since strategy-service and creative-service already query `creative_asset_performance` for winner detection, **Meta performance data automatically flows into the learning loop** without any changes to those services.

### Database Tables (V5 Migration)
| Table | Purpose |
|---|---|
| `ad_platform_connections` | Multi-platform connection credentials + status |
| `ad_platform_ads` | Synced ad metadata from external platforms |
| `ad_platform_insights` | Daily performance metrics from external platforms |
| `creative_asset_platform_mapping` | Links external ads to internal creative assets |

### Creative Asset Mapping
The mapper uses 3-tier matching with confidence scoring:
| Method | Confidence | Logic |
|---|---|---|
| `METADATA_MATCH` | 0.9 | Asset `conceptName` appears in Meta ad name |
| `NAME_MATCH` | 0.5–0.8 | Hook words from asset metadata match proportionally |
| `METADATA_MATCH` (secondary) | 0.4–0.6 | Headline/CTA from asset appears in creative name |

Minimum threshold: 0.3 confidence to create a mapping. Below that, the ad remains unmapped.

### Environment Variables (Phase 4)
Add to `infra/.env`:
```bash
META_API_BASE_URL=https://graph.facebook.com
META_API_VERSION=v21.0
META_API_TIMEOUT_SECONDS=30
META_SYNC_LOOKBACK_DAYS=30
META_SYNC_INTERVAL_MS=3600000
META_SYNC_INITIAL_DELAY_MS=60000
PLATFORM_TOKEN_ENCRYPTION_KEY=        # base64-encoded 32-byte key; leave empty for local dev (plaintext storage)
```

Generate an encryption key:
```bash
openssl rand -base64 32
```

### Testing Phase 4
```bash
cd analytics-service && mvn test
# Runs 32 tests including:
#   AdPlatformIntegrationControllerTest — connect validation, list, disconnect, sync, insights
#   TokenEncryptorTest — encrypt/decrypt round-trip, plaintext fallback, null handling, IV uniqueness
#   MetaInsightNormalizerTest — basic normalization, conversion extraction, ROAS computation
#   MetaCreativeAssetMapperTest — metadata match, name match, threshold filtering, best-match selection
```

---

## Winner Detection + Optimization Recommendations Engine

### Overview
The platform now includes a deterministic scoring engine that classifies every creative asset as **WINNER**, **WEAK**, **TESTING**, or **INSUFFICIENT_DATA** based on aggregated performance metrics, and generates actionable optimization recommendations.

### Classification Thresholds (configurable via environment)
| Classification | Rule |
|---|---|
| `INSUFFICIENT_DATA` | impressions < `CREATIVE_WINNER_MIN_IMPRESSIONS` (default 3000) |
| `WINNER` | ROAS ≥ `CREATIVE_WINNER_MIN_ROAS` (2.0) AND clicks ≥ `CREATIVE_WINNER_MIN_CLICKS` (100) AND conversions ≥ `CREATIVE_WINNER_MIN_CONVERSIONS` (3) |
| `WEAK` | ROAS ≤ `CREATIVE_WEAK_MAX_ROAS` (0.8) with sufficient data |
| `TESTING` | Everything else (metrics between weak and winner thresholds) |

### Performance Score (0–100)
Weighted composite: CTR (25%), ROAS (35%), conversions (25%), CPA efficiency (15%).

### Confidence Score (0–1)
Based on volume sufficiency (impressions/clicks/conversions relative to thresholds) and signal consistency (do all metrics point the same direction).

### New Analytics Endpoints

```bash
# Get winners
curl 'localhost:8080/analytics/creative-assets/winners?businessId=<uuid>&days=30&limit=50'

# Get underperformers
curl 'localhost:8080/analytics/creative-assets/losers?businessId=<uuid>&days=30'

# Get assets still in testing
curl 'localhost:8080/analytics/creative-assets/testing?businessId=<uuid>&days=30'

# Full scorecard (counts by classification)
curl 'localhost:8080/analytics/creative-assets/scorecard?businessId=<uuid>&days=30'

# Optimization recommendations (SCALE / STOP / TEST_MORE / DUPLICATE_WINNER / ADAPT_FOR_PLATFORM)
curl 'localhost:8080/analytics/creative-assets/recommendations?businessId=<uuid>&days=30'

# Detailed insights with breakdown and best/worst metrics
curl 'localhost:8080/analytics/creative-assets/insights?businessId=<uuid>&days=30'

# Force reclassification of all assets
curl -X POST 'localhost:8080/analytics/creative-assets/recompute?businessId=<uuid>&days=30'
```

### Automatic Scoring
- **On ingest:** `POST /analytics/creative-assets/metrics/ingest` now computes classification, performance score, confidence score, and reasoning JSON for each ingested metric.
- **On sync:** `POST /analytics/integrations/meta/sync` automatically reclassifies all assets and generates fresh recommendations after each Meta Ads sync.

### Cross-Service Integration

**strategy-service:**
- Winner insights now include `classification`, `performanceScore`, and `confidenceScore`.
- Weak assets generate `optimizationSignals` in the strategy response.
- `recommendedNextCreativeMoves` include actions to avoid weak patterns and scale winners.

**creative-service:**
- Weak creative patterns are injected into the prompt as "AVOID THESE PATTERNS".
- Response includes `avoidsWeakPatterns: true` when weak-pattern avoidance is active.

**generation-service:**
- Three new variation types: `similar`, `fresh-angle`, `platform-adapted`.
- `POST /generate/from-winner` response now includes `winnerClassification`, `winnerPerformanceScore`, `winnerConfidenceScore`.

### Environment Variables
| Variable | Default | Description |
|---|---|---|
| `CREATIVE_WINNER_MIN_IMPRESSIONS` | `3000` | Minimum impressions for classification |
| `CREATIVE_WINNER_MIN_CLICKS` | `100` | Minimum clicks for WINNER |
| `CREATIVE_WINNER_MIN_CONVERSIONS` | `3` | Minimum conversions for WINNER |
| `CREATIVE_WINNER_MIN_ROAS` | `2.0` | Minimum ROAS for WINNER |
| `CREATIVE_WEAK_MAX_ROAS` | `0.8` | Maximum ROAS before WEAK classification |
| `CREATIVE_MIN_CONFIDENCE` | `0.60` | Minimum confidence to trust a classification |

### Database Migration
Flyway `V6__winner_detection.sql` adds:
- `performance_score`, `classification`, `confidence_score`, `reasoning_json`, `updated_at` columns to `creative_asset_performance`
- `creative_optimization_recommendations` table with indexes on business_id, status, and created_at

### Testing Phase 5
```bash
# Analytics service — 62 tests
cd analytics-service && mvn clean test

# Generation service — 32 tests
cd generation-service && python3 -m pytest test_app.py -v

# New test classes:
#   CreativeWinnerScoringServiceTest — classify, performanceScore, confidence, buildReasoning, scoreAllAssets
#   CreativeOptimizationRecommendationServiceTest — empty, SCALE, STOP, TEST_MORE, mixed, persist, fields
#   CreativeAssetPerformanceControllerTest — all 7 endpoints, classifyDelegation, data assertions
#   TestVariationPromptBuilder — similar, fresh-angle, platform-adapted, classification in prompt
#   TestFromWinnerResponseClassification — classification fields in response
```

---

## Phase 6 — Recommendation Action Layer

Recommendations are now operational. Users can apply, dismiss, generate creative variants from, and export launch-ready packages from any recommendation.

### Recommendation Lifecycle

```
  OPEN ─── apply()  ──> APPLIED
  OPEN ─── dismiss() ──> DISMISSED
```

- **Apply** marks the recommendation as acted-on with a timestamp. Idempotent.
- **Dismiss** marks it as rejected. Idempotent.
- Transitions are one-way: APPLIED cannot be dismissed, DISMISSED cannot be applied.

### New Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/analytics/recommendations/{id}/apply` | Mark recommendation as APPLIED |
| POST | `/analytics/recommendations/{id}/dismiss` | Mark recommendation as DISMISSED |
| GET | `/analytics/recommendations/{id}` | Get full detail with available actions |
| GET | `/analytics/recommendations?businessId=&status=&days=30` | List recommendations with optional filters |
| GET | `/analytics/recommendations/dashboard?businessId=` | Dashboard view grouped by priority |
| GET | `/analytics/recommendations/{id}/export-launch-package?businessId=` | Export execution-ready launch package |
| POST | `/generate/creative-assets/from-recommendation` | Generate new assets based on a recommendation |

### Example: Apply a Recommendation
```bash
curl -X POST http://localhost:8080/analytics/recommendations/{recId}/apply
```

### Example: Dashboard
```bash
curl "http://localhost:8080/analytics/recommendations/dashboard?businessId={businessId}"
```

Returns recommendations grouped by HIGH / MEDIUM / LOW priority, each with `availableActions` (APPLY, DISMISS, GENERATE_VARIANTS, EXPORT_PACKAGE).

### Example: Generate Variants from Recommendation
```bash
curl -X POST http://localhost:8080/generate/creative-assets/from-recommendation \
  -H "Content-Type: application/json" \
  -d '{"recommendationId":"<uuid>","count":3,"variationMode":"similar"}'
```

`variationMode` options: `similar`, `fresh-angle`, `platform-adapted`.

STOP recommendations are blocked from generation unless `metadata_json.allow_generate` is true.

### Example: Export Launch Package
```bash
curl "http://localhost:8080/analytics/recommendations/{recId}/export-launch-package?businessId={businessId}"
```

Returns a ready-to-execute package with: `campaignName`, `platform`, `objective`, `budgetGuidance`, `targetingGuidance`, `copy` (headline/primaryText/cta), `assetLinks`, `landingPageGuidance`, `trackingChecklist`, `notes`.

### Strategy & Creative Awareness

Both `strategy-service` and `creative-service` now query open recommendations and inject them as optimization signals into their LLM prompts. This means generated strategies and creative concepts are influenced by the recommendation engine's findings.

### Database Migration

Flyway `V7__recommendation_action_layer.sql` adds:
- `suggested_next_action`, `applied_at`, `dismissed_at`, `metadata_json`, `updated_at` columns to `creative_optimization_recommendations`
- Index on `created_at` for time-range queries

### Testing Phase 6
```bash
# Analytics service — 100 tests
cd analytics-service && mvn clean test

# Generation service — 45 tests
cd generation-service && python3 -m pytest test_app.py -v

# New test classes:
#   RecommendationActionControllerTest — apply, dismiss, detail, list, dashboard, export-launch-package (16 tests)
#   RecommendationActionServiceTest — apply/dismiss lifecycle, idempotency, dashboard grouping, export launch package (22 tests)
#   TestFromRecommendationValidation — missing fields, invalid modes, count limits
#   TestFromRecommendationEndpoint — stubbed generation, not-found, STOP blocking, STOP allowed via metadata, platform override
#   TestRecommendationPromptBuilder — type-specific directives, winning context, performance data
```

