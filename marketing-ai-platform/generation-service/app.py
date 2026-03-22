import json
import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Optional

import httpx
from fastapi import FastAPI, Query, Request
from fastapi.responses import JSONResponse
from psycopg2 import connect
from psycopg2.extras import RealDictCursor, Json
from pydantic import BaseModel, Field

logging.basicConfig(
    level=logging.INFO,
    format='{"level":"%(levelname)s","service":"generation-service","requestId":"%(request_id)s","message":"%(message)s"}',
)
logger = logging.getLogger(__name__)

app = FastAPI()

DB_DSN = os.getenv(
    "PY_DB_DSN",
    "dbname=marketing_ai user=postgres password=change_me host=postgres port=5432",
)
OPENAI_IMAGE_API_KEY = os.getenv("OPENAI_IMAGE_API_KEY", "")
OPENAI_IMAGE_MODEL = os.getenv("OPENAI_IMAGE_MODEL", "dall-e-3")
OPENAI_IMAGE_BASE_URL = os.getenv("OPENAI_IMAGE_BASE_URL", "https://api.openai.com/v1/images/generations")

OPENAI_CHAT_API_KEY = os.getenv("OPENAI_CHAT_API_KEY", "")
OPENAI_CHAT_MODEL = os.getenv("OPENAI_CHAT_MODEL", "gpt-4o-mini")
OPENAI_CHAT_BASE_URL = os.getenv("OPENAI_CHAT_BASE_URL", "https://api.openai.com/v1/chat/completions")
OPENAI_CHAT_MAX_TOKENS = int(os.getenv("OPENAI_CHAT_MAX_TOKENS", "4096"))


# ---------------------------------------------------------------------------
# Middleware & error handling (matches existing repo patterns)
# ---------------------------------------------------------------------------

@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    request_id = request.headers.get("X-Request-Id", str(uuid.uuid4()))
    request.state.request_id = request_id
    response = await call_next(request)
    response.headers["X-Request-Id"] = request_id
    return response


@app.exception_handler(Exception)
async def err_handler(request: Request, exc: Exception):
    logger.error("Unhandled error: %s", str(exc), extra={"request_id": getattr(request.state, "request_id", "unknown")})
    return JSONResponse(status_code=500, content={
        "requestId": getattr(request.state, "request_id", "unknown"),
        "error": "INTERNAL_ERROR",
        "message": str(exc),
        "details": {},
    })


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------

class TrendContext(BaseModel):
    industry: Optional[str] = None
    keywords: Optional[list[str]] = None


class AssetMetadata(BaseModel):
    hook: Optional[str] = None
    headline: Optional[str] = None
    cta: Optional[str] = None
    conceptName: Optional[str] = None
    visualStyle: Optional[str] = None
    emotionalAngle: Optional[str] = None
    sceneDescription: Optional[str] = None
    compositionNotes: Optional[str] = None
    lightingNotes: Optional[str] = None
    brandTone: Optional[str] = None
    productFocus: Optional[str] = None


class CreativeAssetRequest(BaseModel):
    businessId: str
    creativeId: Optional[str] = None
    strategyRequestId: Optional[str] = None
    assetType: str = Field(..., pattern=r"^(image|video|carousel)$")
    platform: Optional[str] = Field(None, pattern=r"^(meta|google|tiktok|youtube)$")
    prompt: Optional[str] = None
    creativeConceptName: Optional[str] = None
    count: int = Field(default=1, ge=1, le=4)
    size: Optional[str] = "1024x1024"
    trendContext: Optional[TrendContext] = None
    metadata: Optional[AssetMetadata] = None


# Backward-compatible legacy request
class GenerateImageRequest(BaseModel):
    businessId: str
    prompt: str
    size: Optional[str] = "1024x1024"


# ---------------------------------------------------------------------------
# Prompt builder
# ---------------------------------------------------------------------------

class CreativeAssetPromptBuilder:
    """Builds rich, visually useful prompts for image/video generation from
    creative blueprint fields, business context, and trend context."""

    PLATFORM_ASPECT_HINTS = {
        "meta": "Square or 4:5 vertical format optimized for Meta feed placement.",
        "google": "Landscape 1.91:1 format optimized for Google Display Network.",
        "tiktok": "9:16 vertical full-screen format optimized for TikTok.",
        "youtube": "16:9 landscape format optimized for YouTube ads.",
    }

    ASSET_TYPE_HINTS = {
        "image": "Single high-impact static image. Sharp focus, clean composition.",
        "video": "Key frame for a short-form video ad. Dynamic energy, motion implied.",
        "carousel": "First card of a swipeable carousel. Must hook attention instantly.",
    }

    @classmethod
    def build(
        cls,
        asset_type: str,
        platform: Optional[str],
        direct_prompt: Optional[str],
        metadata: Optional[AssetMetadata],
        trend_context: Optional[TrendContext],
        business_info: Optional[dict],
    ) -> str:
        if direct_prompt and not metadata:
            return direct_prompt

        parts: list[str] = []

        # Core scene / concept
        if metadata and metadata.sceneDescription:
            parts.append(f"Scene: {metadata.sceneDescription}")
        elif metadata and metadata.conceptName:
            parts.append(f"Concept: {metadata.conceptName}")
        elif direct_prompt:
            parts.append(direct_prompt)

        # Product focus
        if metadata and metadata.productFocus:
            parts.append(f"Product focus: {metadata.productFocus}")
        elif business_info and business_info.get("product"):
            parts.append(f"Product: {business_info['product']}")

        # Visual style
        if metadata and metadata.visualStyle:
            parts.append(f"Visual style: {metadata.visualStyle}")

        # Emotional angle and mood
        if metadata and metadata.emotionalAngle:
            parts.append(f"Mood/emotion: {metadata.emotionalAngle}")

        # Brand tone
        if metadata and metadata.brandTone:
            parts.append(f"Brand tone: {metadata.brandTone}")

        # Composition and lighting
        if metadata and metadata.compositionNotes:
            parts.append(f"Composition: {metadata.compositionNotes}")
        if metadata and metadata.lightingNotes:
            parts.append(f"Lighting: {metadata.lightingNotes}")

        # Hook / headline overlay context
        if metadata and metadata.hook:
            parts.append(f"Ad hook context: {metadata.hook}")
        if metadata and metadata.headline:
            parts.append(f"Headline context: {metadata.headline}")

        # Trend injection (subtle)
        if trend_context and trend_context.keywords:
            trend_str = ", ".join(trend_context.keywords[:5])
            parts.append(f"Current trend direction: {trend_str}")

        # Platform and format hints
        if asset_type in cls.ASSET_TYPE_HINTS:
            parts.append(cls.ASSET_TYPE_HINTS[asset_type])
        if platform and platform in cls.PLATFORM_ASPECT_HINTS:
            parts.append(cls.PLATFORM_ASPECT_HINTS[platform])

        # Industry context
        if business_info and business_info.get("industry"):
            parts.append(f"Industry: {business_info['industry']}")

        # Safety: professional advertising content
        parts.append("Professional advertising photography. Clean, brand-safe, commercial quality.")

        return " | ".join(parts) if parts else "Professional product advertisement image."


# ---------------------------------------------------------------------------
# DB helpers
# ---------------------------------------------------------------------------

def _get_connection():
    return connect(DB_DSN)


def _fetch_business(business_id: str) -> Optional[dict]:
    try:
        with _get_connection() as conn:
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                cur.execute(
                    "SELECT business_name, industry, product, price_range, location, target_audience "
                    "FROM business_profile WHERE id = %s",
                    (business_id,),
                )
                row = cur.fetchone()
                return dict(row) if row else None
    except Exception as e:
        logger.warning("Failed to fetch business %s: %s", business_id, e, extra={"request_id": "db"})
        return None


def _persist_asset(
    asset_id: str,
    business_id: str,
    creative_id: Optional[str],
    strategy_request_id: Optional[str],
    asset_type: str,
    platform: Optional[str],
    prompt_text: str,
    provider: str,
    provider_asset_id: Optional[str],
    asset_url: Optional[str],
    thumbnail_url: Optional[str],
    status: str,
    trend_context: Optional[dict],
    metadata: Optional[dict],
):
    with _get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """INSERT INTO creative_assets
                   (id, business_id, creative_id, strategy_request_id, asset_type,
                    platform, prompt_text, provider, provider_asset_id, asset_url,
                    thumbnail_url, status, trend_context_json, metadata_json, created_at)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (
                    asset_id,
                    business_id,
                    creative_id,
                    strategy_request_id,
                    asset_type,
                    platform,
                    prompt_text,
                    provider,
                    provider_asset_id,
                    asset_url,
                    thumbnail_url,
                    status,
                    Json(trend_context) if trend_context else None,
                    Json(metadata) if metadata else None,
                    datetime.now(timezone.utc),
                ),
            )
        conn.commit()


def _query_assets(business_id: str, limit: int) -> list[dict]:
    with _get_connection() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                """SELECT id, business_id, creative_id, strategy_request_id, asset_type,
                          platform, prompt_text, provider, provider_asset_id, asset_url,
                          thumbnail_url, status, trend_context_json, metadata_json, created_at
                   FROM creative_assets
                   WHERE business_id = %s
                   ORDER BY created_at DESC
                   LIMIT %s""",
                (business_id, limit),
            )
            return [dict(r) for r in cur.fetchall()]


def _query_asset_by_id(asset_id: str) -> Optional[dict]:
    with _get_connection() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                """SELECT id, business_id, creative_id, strategy_request_id, asset_type,
                          platform, prompt_text, provider, provider_asset_id, asset_url,
                          thumbnail_url, status, trend_context_json, metadata_json, created_at
                   FROM creative_assets
                   WHERE id = %s""",
                (asset_id,),
            )
            row = cur.fetchone()
            return dict(row) if row else None


# ---------------------------------------------------------------------------
# Provider: real OpenAI DALL-E integration (if configured)
# ---------------------------------------------------------------------------

def _call_openai_image(prompt: str, size: str, count: int) -> list[dict]:
    """Call OpenAI DALL-E API. Returns list of {url, revised_prompt} dicts."""
    results = []
    # DALL-E 3 supports n=1 only; loop for count
    for _ in range(count):
        with httpx.Client(timeout=60.0) as client:
            resp = client.post(
                OPENAI_IMAGE_BASE_URL,
                headers={
                    "Authorization": f"Bearer {OPENAI_IMAGE_API_KEY}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": OPENAI_IMAGE_MODEL,
                    "prompt": prompt,
                    "n": 1,
                    "size": size,
                },
            )
            resp.raise_for_status()
            data = resp.json()
            for item in data.get("data", []):
                results.append({
                    "url": item.get("url"),
                    "revised_prompt": item.get("revised_prompt"),
                })
    return results


def _is_provider_configured() -> bool:
    return bool(OPENAI_IMAGE_API_KEY)


def _is_chat_configured() -> bool:
    return bool(OPENAI_CHAT_API_KEY)


def _call_openai_chat(system_prompt: str, user_prompt: str, temperature: float = 0.7) -> str:
    """Call OpenAI Chat Completions API. Returns the assistant message content."""
    with httpx.Client(timeout=90.0) as client:
        resp = client.post(
            OPENAI_CHAT_BASE_URL,
            headers={
                "Authorization": f"Bearer {OPENAI_CHAT_API_KEY}",
                "Content-Type": "application/json",
            },
            json={
                "model": OPENAI_CHAT_MODEL,
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                "max_tokens": OPENAI_CHAT_MAX_TOKENS,
                "temperature": temperature,
            },
        )
        resp.raise_for_status()
        data = resp.json()
        choices = data.get("choices", [])
        if not choices:
            raise RuntimeError("OpenAI returned no choices")
        return choices[0]["message"]["content"]


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/generate/image")
def generate_image_legacy(req: GenerateImageRequest, request: Request):
    """Backward-compatible legacy endpoint."""
    return {
        "requestId": request.state.request_id,
        "assetId": str(uuid.uuid4()),
        "status": "STUBBED",
        "message": "Use POST /generate/creative-assets for full asset generation. "
                   "Provide OPENAI_IMAGE_API_KEY env var for real generation.",
    }


@app.post("/generate/creative-assets")
def generate_creative_assets(req: CreativeAssetRequest, request: Request):
    """Generate creative assets from a creative blueprint or direct prompt."""
    request_id = request.state.request_id
    logger.info(
        "Generate creative assets businessId=%s assetType=%s count=%d",
        req.businessId, req.assetType, req.count,
        extra={"request_id": request_id},
    )

    # Fetch business context for prompt enrichment
    business_info = _fetch_business(req.businessId)
    if not business_info:
        return JSONResponse(status_code=400, content={
            "requestId": request_id,
            "error": "BAD_REQUEST",
            "message": f"Business not found: {req.businessId}",
            "details": {},
        })

    # Build prompt
    final_prompt = CreativeAssetPromptBuilder.build(
        asset_type=req.assetType,
        platform=req.platform,
        direct_prompt=req.prompt,
        metadata=req.metadata,
        trend_context=req.trendContext,
        business_info=business_info,
    )

    # Prepare metadata for storage
    meta_dict = req.metadata.model_dump(exclude_none=True) if req.metadata else {}
    if req.creativeConceptName:
        meta_dict["conceptName"] = req.creativeConceptName
    trend_dict = req.trendContext.model_dump(exclude_none=True) if req.trendContext else None

    assets_response = []
    overall_status = "SUCCESS"

    use_real_provider = _is_provider_configured() and req.assetType == "image"

    for i in range(req.count):
        asset_id = str(uuid.uuid4())
        provider = "STUB"
        provider_asset_id = None
        asset_url = None
        thumbnail_url = None
        status = "STUBBED"

        if use_real_provider:
            try:
                results = _call_openai_image(final_prompt, req.size or "1024x1024", 1)
                if results:
                    provider = "OPENAI"
                    provider_asset_id = None
                    asset_url = results[0].get("url")
                    thumbnail_url = asset_url
                    status = "SUCCESS"
                else:
                    status = "FAILED"
                    overall_status = "FAILED"
            except Exception as e:
                logger.error(
                    "OpenAI image generation failed: %s", str(e),
                    extra={"request_id": request_id},
                )
                status = "FAILED"
                overall_status = "FAILED"
        else:
            # Stubbed: generate a deterministic placeholder
            provider = "STUB"
            status = "STUBBED"
            if overall_status != "FAILED":
                overall_status = "STUBBED"

        # Persist record
        _persist_asset(
            asset_id=asset_id,
            business_id=req.businessId,
            creative_id=req.creativeId,
            strategy_request_id=req.strategyRequestId,
            asset_type=req.assetType,
            platform=req.platform,
            prompt_text=final_prompt,
            provider=provider,
            provider_asset_id=provider_asset_id,
            asset_url=asset_url,
            thumbnail_url=thumbnail_url,
            status=status,
            trend_context=trend_dict,
            metadata=meta_dict if meta_dict else None,
        )

        assets_response.append({
            "assetId": asset_id,
            "assetType": req.assetType,
            "status": status,
            "url": asset_url,
            "thumbnailUrl": thumbnail_url,
            "provider": provider,
            "providerAssetId": provider_asset_id,
            "promptUsed": final_prompt,
        })

    logger.info(
        "Generated %d assets status=%s", len(assets_response), overall_status,
        extra={"request_id": request_id},
    )

    return {
        "requestId": request_id,
        "status": overall_status,
        "assets": assets_response,
    }


class FromWinnerRequest(BaseModel):
    winnerAssetId: str
    businessId: str
    variationType: str = Field(default="iteration", pattern=r"^(iteration|remix|opposite|similar|fresh-angle|platform-adapted)$")
    assetType: Optional[str] = Field(None, pattern=r"^(image|video|carousel)$")
    platform: Optional[str] = Field(None, pattern=r"^(meta|google|tiktok|youtube)$")
    count: int = Field(default=1, ge=1, le=4)
    size: Optional[str] = "1024x1024"


class FromRecommendationRequest(BaseModel):
    recommendationId: str
    count: int = Field(default=3, ge=1, le=4)
    variationMode: str = Field(default="similar", pattern=r"^(similar|fresh-angle|platform-adapted)$")


def _query_winner_asset(asset_id: str) -> Optional[dict]:
    """Fetch a creative asset and its performance data for winner-based generation."""
    with _get_connection() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                """SELECT ca.id, ca.business_id, ca.asset_type, ca.platform, ca.prompt_text,
                          ca.metadata_json, ca.trend_context_json,
                          COALESCE(SUM(cap.impressions),0) as total_impressions,
                          COALESCE(SUM(cap.clicks),0) as total_clicks,
                          COALESCE(SUM(cap.conversions),0) as total_conversions,
                          COALESCE(AVG(cap.roas),0) as avg_roas,
                          MAX(cap.classification) as classification,
                          MAX(cap.performance_score) as performance_score,
                          MAX(cap.confidence_score) as confidence_score
                   FROM creative_assets ca
                   LEFT JOIN creative_asset_performance cap ON cap.creative_asset_id = ca.id
                   WHERE ca.id = %s
                   GROUP BY ca.id""",
                (asset_id,),
            )
            row = cur.fetchone()
            return dict(row) if row else None


def _build_variation_prompt(winner: dict, variation_type: str, business_info: Optional[dict]) -> str:
    """Build a prompt that creates a variation of a winning asset."""
    original_prompt = winner.get("prompt_text", "")
    metadata = winner.get("metadata_json") or {}
    if isinstance(metadata, str):
        import json as _json
        metadata = _json.loads(metadata)

    parts: list[str] = []

    if variation_type == "iteration":
        parts.append("Create an improved iteration of this winning ad creative.")
        parts.append(f"Original concept: {original_prompt}")
        if metadata.get("hook"):
            parts.append(f"Winning hook pattern: {metadata['hook']}")
        if metadata.get("visualStyle"):
            parts.append(f"Keep visual style: {metadata['visualStyle']}")
        if metadata.get("emotionalAngle"):
            parts.append(f"Keep emotional angle: {metadata['emotionalAngle']}")
        parts.append("Enhance and refine while keeping the core winning elements.")

    elif variation_type == "remix":
        parts.append("Create a remixed variation of this successful ad creative with a fresh take.")
        parts.append(f"Original concept: {original_prompt}")
        if metadata.get("hook"):
            parts.append(f"Original hook: {metadata['hook']} — create a new hook with similar energy")
        if metadata.get("visualStyle"):
            parts.append(f"Original style: {metadata['visualStyle']} — try a complementary style")
        parts.append("Keep what works but bring fresh creative energy.")

    elif variation_type == "opposite":
        parts.append("Create a contrasting version of this ad creative to test against the original.")
        parts.append(f"Original concept: {original_prompt}")
        if metadata.get("visualStyle"):
            style_opposites = {"UGC": "editorial", "minimal": "bold-graphic", "bold-graphic": "minimal",
                               "lifestyle": "studio", "editorial": "UGC"}
            opposite_style = style_opposites.get(metadata["visualStyle"], "contrasting")
            parts.append(f"Use contrasting visual style: {opposite_style}")
        parts.append("Create the opposite approach while targeting the same audience.")

    elif variation_type == "similar":
        parts.append("Create a close variation of this winning ad creative, keeping the same core approach.")
        parts.append(f"Original concept: {original_prompt}")
        if metadata.get("hook"):
            parts.append(f"Keep similar hook pattern: {metadata['hook']}")
        if metadata.get("visualStyle"):
            parts.append(f"Keep same visual style: {metadata['visualStyle']}")
        if metadata.get("emotionalAngle"):
            parts.append(f"Keep same emotional angle: {metadata['emotionalAngle']}")
        parts.append("Make subtle variations — different product angle, slightly different copy, same winning formula.")

    elif variation_type == "fresh-angle":
        parts.append("Create a fresh-angle variation of this winning ad creative with a completely new perspective.")
        parts.append(f"Original concept: {original_prompt}")
        if metadata.get("hook"):
            parts.append(f"Original hook was: {metadata['hook']} — find a completely different hook that targets the same desire")
        parts.append("Same product, same audience, but entirely different creative angle and messaging approach.")

    elif variation_type == "platform-adapted":
        target_platform = winner.get("platform", "meta")
        parts.append(f"Adapt this winning creative for optimal performance on {target_platform}.")
        parts.append(f"Original concept: {original_prompt}")
        if metadata.get("hook"):
            parts.append(f"Winning hook: {metadata['hook']}")
        platform_guidance = {
            "meta": "Optimize for Facebook/Instagram: bold visuals, short punchy copy, emotional hooks.",
            "google": "Optimize for Google Ads: clear value proposition, direct CTA, product-focused.",
            "tiktok": "Optimize for TikTok: native UGC feel, trend-aware, fast-paced, authentic.",
            "youtube": "Optimize for YouTube: cinematic quality, story-driven, longer format hook.",
        }
        parts.append(platform_guidance.get(target_platform, "Adapt for the platform's best practices."))

    if business_info:
        if business_info.get("product"):
            parts.append(f"Product: {business_info['product']}")
        if business_info.get("industry"):
            parts.append(f"Industry: {business_info['industry']}")

    perf = []
    if winner.get("avg_roas"):
        perf.append(f"ROAS: {winner['avg_roas']:.2f}")
    if winner.get("total_conversions"):
        perf.append(f"Conversions: {winner['total_conversions']}")
    if winner.get("classification"):
        perf.append(f"Classification: {winner['classification']}")
    if winner.get("performance_score"):
        perf.append(f"Score: {winner['performance_score']:.2f}")
    if perf:
        parts.append(f"Original performance: {', '.join(perf)}")

    parts.append("Professional advertising photography. Clean, brand-safe, commercial quality.")
    return " | ".join(parts)


@app.post("/generate/creative-assets/from-winner")
def generate_from_winner(req: FromWinnerRequest, request: Request):
    """Generate new creative assets based on a winning asset's DNA."""
    request_id = request.state.request_id
    logger.info(
        "Generate from winner winnerAssetId=%s variationType=%s count=%d",
        req.winnerAssetId, req.variationType, req.count,
        extra={"request_id": request_id},
    )

    # Fetch winner asset with performance data
    winner = _query_winner_asset(req.winnerAssetId)
    if not winner:
        return JSONResponse(status_code=404, content={
            "requestId": request_id,
            "error": "NOT_FOUND",
            "message": f"Winner asset not found: {req.winnerAssetId}",
            "details": {},
        })

    # Verify business ownership
    if str(winner["business_id"]) != req.businessId:
        return JSONResponse(status_code=400, content={
            "requestId": request_id,
            "error": "BAD_REQUEST",
            "message": "Asset does not belong to the specified business",
            "details": {},
        })

    business_info = _fetch_business(req.businessId)
    asset_type = req.assetType or winner.get("asset_type", "image")
    platform = req.platform or winner.get("platform")

    # Build variation prompt from winner DNA
    final_prompt = _build_variation_prompt(winner, req.variationType, business_info)

    # Preserve winner metadata with variation context
    winner_metadata = winner.get("metadata_json") or {}
    if isinstance(winner_metadata, str):
        winner_metadata = json.loads(winner_metadata)
    meta_dict = dict(winner_metadata)
    meta_dict["basedOnWinnerId"] = req.winnerAssetId
    meta_dict["variationType"] = req.variationType
    meta_dict["originalPrompt"] = winner.get("prompt_text", "")

    trend_dict = winner.get("trend_context_json")
    if isinstance(trend_dict, str):
        trend_dict = json.loads(trend_dict)

    assets_response = []
    overall_status = "SUCCESS"
    use_real_provider = _is_provider_configured() and asset_type == "image"

    for i in range(req.count):
        asset_id = str(uuid.uuid4())
        provider = "STUB"
        provider_asset_id = None
        asset_url = None
        thumbnail_url = None
        status = "STUBBED"

        if use_real_provider:
            try:
                results = _call_openai_image(final_prompt, req.size or "1024x1024", 1)
                if results:
                    provider = "OPENAI"
                    asset_url = results[0].get("url")
                    thumbnail_url = asset_url
                    status = "SUCCESS"
                else:
                    status = "FAILED"
                    overall_status = "FAILED"
            except Exception as e:
                logger.error("OpenAI image generation failed: %s", str(e), extra={"request_id": request_id})
                status = "FAILED"
                overall_status = "FAILED"
        else:
            if overall_status != "FAILED":
                overall_status = "STUBBED"

        _persist_asset(
            asset_id=asset_id,
            business_id=req.businessId,
            creative_id=None,
            strategy_request_id=None,
            asset_type=asset_type,
            platform=platform,
            prompt_text=final_prompt,
            provider=provider,
            provider_asset_id=provider_asset_id,
            asset_url=asset_url,
            thumbnail_url=thumbnail_url,
            status=status,
            trend_context=trend_dict if isinstance(trend_dict, dict) else None,
            metadata=meta_dict,
        )

        assets_response.append({
            "assetId": asset_id,
            "assetType": asset_type,
            "status": status,
            "url": asset_url,
            "thumbnailUrl": thumbnail_url,
            "provider": provider,
            "promptUsed": final_prompt,
            "basedOnWinnerId": req.winnerAssetId,
            "variationType": req.variationType,
        })

    return {
        "requestId": request_id,
        "status": overall_status,
        "winnerAssetId": req.winnerAssetId,
        "variationType": req.variationType,
        "winnerClassification": winner.get("classification"),
        "winnerPerformanceScore": float(winner["performance_score"]) if winner.get("performance_score") else None,
        "winnerConfidenceScore": float(winner["confidence_score"]) if winner.get("confidence_score") else None,
        "assets": assets_response,
    }


# ---------------------------------------------------------------------------
# From-Recommendation generation
# ---------------------------------------------------------------------------

def _query_recommendation(recommendation_id: str) -> Optional[dict]:
    """Fetch a recommendation from the creative_optimization_recommendations table."""
    try:
        with _get_connection() as conn:
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                cur.execute(
                    """SELECT id, business_id, creative_asset_id, recommendation_type,
                              priority, title, description, reasoning_json,
                              suggested_next_action, status, metadata_json
                       FROM creative_optimization_recommendations
                       WHERE id = %s""",
                    (recommendation_id,),
                )
                row = cur.fetchone()
                return dict(row) if row else None
    except Exception as e:
        logger.warning("Failed to fetch recommendation %s: %s", recommendation_id, e, extra={"request_id": "db"})
        return None


def _build_recommendation_prompt(
    rec: dict,
    winner: Optional[dict],
    business_info: Optional[dict],
    variation_mode: str,
) -> str:
    """Build a generation prompt informed by recommendation type and variation mode."""
    rec_type = rec.get("recommendation_type", "")
    parts: list[str] = []

    # Core directive from recommendation type
    type_directives = {
        "SCALE": "Create closely related variants of this high-performing asset. Preserve the winning formula.",
        "TEST_MORE": "Create controlled test variations. Change one variable at a time from the original.",
        "DUPLICATE_WINNER": "Create similar variants that replicate the winning structure and approach.",
        "ADAPT_FOR_PLATFORM": "Adapt this winning creative for a different platform while keeping core appeal.",
    }
    parts.append(type_directives.get(rec_type, "Create a new creative variant based on this recommendation."))

    # Recommendation context
    if rec.get("title"):
        parts.append(f"Recommendation: {rec['title']}")
    if rec.get("description"):
        parts.append(f"Context: {rec['description']}")

    # Source asset context if available
    if winner:
        original_prompt = winner.get("prompt_text", "")
        if original_prompt:
            parts.append(f"Original concept: {original_prompt}")

        metadata = winner.get("metadata_json") or {}
        if isinstance(metadata, str):
            metadata = json.loads(metadata) if metadata else {}

        if metadata.get("hook"):
            parts.append(f"Winning hook: {metadata['hook']}")
        if metadata.get("visualStyle"):
            parts.append(f"Visual style: {metadata['visualStyle']}")
        if metadata.get("emotionalAngle"):
            parts.append(f"Emotional angle: {metadata['emotionalAngle']}")

        perf = []
        if winner.get("avg_roas"):
            perf.append(f"ROAS: {winner['avg_roas']:.2f}")
        if winner.get("total_conversions"):
            perf.append(f"Conversions: {winner['total_conversions']}")
        if perf:
            parts.append(f"Performance: {', '.join(perf)}")

    # Variation mode modifier
    if variation_mode == "similar":
        parts.append("Make subtle variations — different angle, slightly different copy, same winning formula.")
    elif variation_mode == "fresh-angle":
        parts.append("Same product, same audience, but entirely different creative angle and messaging approach.")
    elif variation_mode == "platform-adapted":
        parts.append("Optimize for the target platform's best practices, format, and audience behavior.")

    # Business context
    if business_info:
        if business_info.get("product"):
            parts.append(f"Product: {business_info['product']}")
        if business_info.get("industry"):
            parts.append(f"Industry: {business_info['industry']}")

    parts.append("Professional advertising photography. Clean, brand-safe, commercial quality.")
    return " | ".join(parts)


@app.post("/generate/creative-assets/from-recommendation")
def generate_from_recommendation(req: FromRecommendationRequest, request: Request):
    """Generate new creative assets based on a recommendation."""
    request_id = request.state.request_id
    logger.info(
        "Generate from recommendation recommendationId=%s variationMode=%s count=%d",
        req.recommendationId, req.variationMode, req.count,
        extra={"request_id": request_id},
    )

    # Fetch recommendation
    rec = _query_recommendation(req.recommendationId)
    if not rec:
        return JSONResponse(status_code=404, content={
            "requestId": request_id,
            "error": "NOT_FOUND",
            "message": f"Recommendation not found: {req.recommendationId}",
            "details": {},
        })

    # Block generation from STOP recommendations unless metadata explicitly allows it
    if rec.get("recommendation_type") == "STOP":
        metadata = rec.get("metadata_json") or {}
        if isinstance(metadata, str):
            metadata = json.loads(metadata) if metadata else {}
        if not metadata.get("allow_generate"):
            return JSONResponse(status_code=400, content={
                "requestId": request_id,
                "error": "BAD_REQUEST",
                "message": "Cannot generate from STOP recommendation unless explicitly allowed via metadata",
                "details": {"recommendationType": "STOP"},
            })

    business_id = str(rec["business_id"])
    business_info = _fetch_business(business_id)

    # Fetch source creative asset if linked
    winner = None
    creative_asset_id = rec.get("creative_asset_id")
    if creative_asset_id:
        winner = _query_winner_asset(str(creative_asset_id))

    # Determine asset type and platform
    asset_type = "image"
    platform = None
    if winner:
        asset_type = winner.get("asset_type", "image") or "image"
        platform = winner.get("platform")

    # For ADAPT_FOR_PLATFORM, override platform from recommendation title/description
    if rec.get("recommendation_type") == "ADAPT_FOR_PLATFORM":
        desc = (rec.get("description") or "").lower()
        title = (rec.get("title") or "").lower()
        for p in ["tiktok", "google", "youtube", "meta"]:
            if f"for {p}" in desc or f"for {p}" in title:
                platform = p
                break

    # Build generation prompt from recommendation context
    final_prompt = _build_recommendation_prompt(rec, winner, business_info, req.variationMode)

    # Prepare metadata for storage
    meta_dict = {}
    if winner:
        winner_metadata = winner.get("metadata_json") or {}
        if isinstance(winner_metadata, str):
            winner_metadata = json.loads(winner_metadata) if winner_metadata else {}
        meta_dict = dict(winner_metadata)
    meta_dict["fromRecommendationId"] = req.recommendationId
    meta_dict["recommendationType"] = rec.get("recommendation_type")
    meta_dict["variationMode"] = req.variationMode
    if creative_asset_id:
        meta_dict["sourceAssetId"] = str(creative_asset_id)

    assets_response = []
    overall_status = "SUCCESS"
    use_real_provider = _is_provider_configured() and asset_type == "image"

    for i in range(req.count):
        asset_id = str(uuid.uuid4())
        provider = "STUB"
        provider_asset_id = None
        asset_url = None
        thumbnail_url = None
        status = "STUBBED"

        if use_real_provider:
            try:
                results = _call_openai_image(final_prompt, "1024x1024", 1)
                if results:
                    provider = "OPENAI"
                    asset_url = results[0].get("url")
                    thumbnail_url = asset_url
                    status = "SUCCESS"
                else:
                    status = "FAILED"
                    overall_status = "FAILED"
            except Exception as e:
                logger.error("OpenAI image generation failed: %s", str(e), extra={"request_id": request_id})
                status = "FAILED"
                overall_status = "FAILED"
        else:
            if overall_status != "FAILED":
                overall_status = "STUBBED"

        _persist_asset(
            asset_id=asset_id,
            business_id=business_id,
            creative_id=None,
            strategy_request_id=None,
            asset_type=asset_type,
            platform=platform,
            prompt_text=final_prompt,
            provider=provider,
            provider_asset_id=provider_asset_id,
            asset_url=asset_url,
            thumbnail_url=thumbnail_url,
            status=status,
            trend_context=None,
            metadata=meta_dict,
        )

        assets_response.append({
            "assetId": asset_id,
            "assetType": asset_type,
            "status": status,
            "url": asset_url,
            "thumbnailUrl": thumbnail_url,
        })

    logger.info(
        "Generated %d assets from recommendation=%s status=%s",
        len(assets_response), req.recommendationId, overall_status,
        extra={"request_id": request_id},
    )

    return {
        "requestId": request_id,
        "recommendationId": req.recommendationId,
        "status": overall_status,
        "assets": assets_response,
    }


@app.get("/generate/assets")
def list_assets(
    businessId: str = Query(...),
    limit: int = Query(default=20, ge=1, le=100),
    request: Request = None,
):
    """Return recent generated assets for a business."""
    rows = _query_assets(businessId, limit)
    items = []
    for r in rows:
        items.append({
            "assetId": str(r["id"]),
            "businessId": str(r["business_id"]),
            "creativeId": str(r["creative_id"]) if r.get("creative_id") else None,
            "strategyRequestId": str(r["strategy_request_id"]) if r.get("strategy_request_id") else None,
            "assetType": r["asset_type"],
            "platform": r["platform"],
            "promptText": r["prompt_text"],
            "provider": r["provider"],
            "providerAssetId": r.get("provider_asset_id"),
            "url": r.get("asset_url"),
            "thumbnailUrl": r.get("thumbnail_url"),
            "status": r["status"],
            "trendContext": r.get("trend_context_json"),
            "metadata": r.get("metadata_json"),
            "createdAt": r["created_at"].isoformat() if r.get("created_at") else None,
        })
    return {"businessId": businessId, "assets": items, "count": len(items)}


@app.get("/generate/assets/{asset_id}")
def get_asset(asset_id: str, request: Request):
    """Return a single generated asset by ID."""
    r = _query_asset_by_id(asset_id)
    if not r:
        return JSONResponse(status_code=404, content={
            "requestId": request.state.request_id,
            "error": "NOT_FOUND",
            "message": f"Asset not found: {asset_id}",
            "details": {},
        })
    return {
        "assetId": str(r["id"]),
        "businessId": str(r["business_id"]),
        "creativeId": str(r["creative_id"]) if r.get("creative_id") else None,
        "strategyRequestId": str(r["strategy_request_id"]) if r.get("strategy_request_id") else None,
        "assetType": r["asset_type"],
        "platform": r["platform"],
        "promptText": r["prompt_text"],
        "provider": r["provider"],
        "providerAssetId": r.get("provider_asset_id"),
        "url": r.get("asset_url"),
        "thumbnailUrl": r.get("thumbnail_url"),
        "status": r["status"],
        "trendContext": r.get("trend_context_json"),
        "metadata": r.get("metadata_json"),
        "createdAt": r["created_at"].isoformat() if r.get("created_at") else None,
    }


# ===========================================================================
# Phase 2 — Landing Page Generator, Offer Generator, Enhanced Launch Package
# ===========================================================================

# ---------------------------------------------------------------------------
# Pydantic models — Phase 2
# ---------------------------------------------------------------------------

class LandingPageSection(BaseModel):
    sectionType: str
    headline: str
    body: str
    ctaText: Optional[str] = None
    ctaUrl: Optional[str] = None


class LandingPageRequest(BaseModel):
    businessId: str
    strategyRequestId: Optional[str] = None
    platform: Optional[str] = Field(None, pattern=r"^(meta|google|tiktok|youtube)$")
    objective: Optional[str] = None
    productFocus: Optional[str] = None
    targetAudience: Optional[str] = None
    tone: Optional[str] = None
    offerHeadline: Optional[str] = None
    landingPageRecommendations: Optional[dict] = None
    offerStrategy: Optional[dict] = None
    creativesNeeded: Optional[list[dict]] = None


class OfferRequest(BaseModel):
    businessId: str
    strategyRequestId: Optional[str] = None
    platform: Optional[str] = Field(None, pattern=r"^(meta|google|tiktok|youtube)$")
    objective: Optional[str] = None
    productFocus: Optional[str] = None
    targetAudience: Optional[str] = None
    tone: Optional[str] = None
    offerType: Optional[str] = Field(
        None,
        pattern=r"^(discount|free-trial|free-shipping|bundle|limited-time|lead-magnet|consultation|demo)$",
    )
    offerStrategy: Optional[dict] = None
    customerPersona: Optional[dict] = None


class EnhancedLaunchPackageRequest(BaseModel):
    businessId: str
    recommendationId: str
    strategyRequestId: Optional[str] = None
    platform: Optional[str] = Field(None, pattern=r"^(meta|google|tiktok|youtube)$")
    landingPageRecommendations: Optional[dict] = None
    offerStrategy: Optional[dict] = None
    customerPersona: Optional[dict] = None
    creativesNeeded: Optional[list[dict]] = None


# ---------------------------------------------------------------------------
# DB helpers — Phase 2
# ---------------------------------------------------------------------------

def _ensure_landing_pages_table():
    """Idempotent table creation for landing pages."""
    with _get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                CREATE TABLE IF NOT EXISTS generated_landing_pages (
                    id              UUID PRIMARY KEY,
                    business_id     UUID NOT NULL,
                    strategy_request_id UUID NULL,
                    platform        TEXT NULL,
                    objective       TEXT NULL,
                    sections_json   JSONB NOT NULL,
                    full_html       TEXT NULL,
                    meta_title      TEXT NULL,
                    meta_description TEXT NULL,
                    prompt_used     TEXT NOT NULL,
                    provider        TEXT NOT NULL,
                    status          TEXT NOT NULL,
                    context_json    JSONB NULL,
                    created_at      TIMESTAMP NOT NULL DEFAULT now()
                )
            """)
        conn.commit()


def _ensure_offers_table():
    """Idempotent table creation for offers."""
    with _get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                CREATE TABLE IF NOT EXISTS generated_offers (
                    id              UUID PRIMARY KEY,
                    business_id     UUID NOT NULL,
                    strategy_request_id UUID NULL,
                    platform        TEXT NULL,
                    offer_type      TEXT NULL,
                    headline        TEXT NOT NULL,
                    description     TEXT NOT NULL,
                    terms           TEXT NULL,
                    urgency_hook    TEXT NULL,
                    cta_primary     TEXT NOT NULL,
                    cta_variants    JSONB NULL,
                    value_proposition TEXT NULL,
                    prompt_used     TEXT NOT NULL,
                    provider        TEXT NOT NULL,
                    status          TEXT NOT NULL,
                    context_json    JSONB NULL,
                    created_at      TIMESTAMP NOT NULL DEFAULT now()
                )
            """)
        conn.commit()


def _ensure_launch_packages_table():
    """Idempotent table creation for enhanced launch packages."""
    with _get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                CREATE TABLE IF NOT EXISTS generated_launch_packages (
                    id                  UUID PRIMARY KEY,
                    business_id         UUID NOT NULL,
                    recommendation_id   UUID NULL,
                    strategy_request_id UUID NULL,
                    platform            TEXT NULL,
                    landing_page_id     UUID NULL,
                    offer_id            UUID NULL,
                    package_json        JSONB NOT NULL,
                    prompt_used         TEXT NOT NULL,
                    provider            TEXT NOT NULL,
                    status              TEXT NOT NULL,
                    created_at          TIMESTAMP NOT NULL DEFAULT now()
                )
            """)
        conn.commit()


def _persist_landing_page(
    page_id: str,
    business_id: str,
    strategy_request_id: Optional[str],
    platform: Optional[str],
    objective: Optional[str],
    sections_json: list,
    full_html: Optional[str],
    meta_title: Optional[str],
    meta_description: Optional[str],
    prompt_used: str,
    provider: str,
    status: str,
    context_json: Optional[dict],
):
    _ensure_landing_pages_table()
    with _get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """INSERT INTO generated_landing_pages
                   (id, business_id, strategy_request_id, platform, objective,
                    sections_json, full_html, meta_title, meta_description,
                    prompt_used, provider, status, context_json, created_at)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (
                    page_id, business_id, strategy_request_id, platform, objective,
                    Json(sections_json), full_html, meta_title, meta_description,
                    prompt_used, provider, status, Json(context_json) if context_json else None,
                    datetime.now(timezone.utc),
                ),
            )
        conn.commit()


def _persist_offer(
    offer_id: str,
    business_id: str,
    strategy_request_id: Optional[str],
    platform: Optional[str],
    offer_type: Optional[str],
    headline: str,
    description: str,
    terms: Optional[str],
    urgency_hook: Optional[str],
    cta_primary: str,
    cta_variants: Optional[list],
    value_proposition: Optional[str],
    prompt_used: str,
    provider: str,
    status: str,
    context_json: Optional[dict],
):
    _ensure_offers_table()
    with _get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """INSERT INTO generated_offers
                   (id, business_id, strategy_request_id, platform, offer_type,
                    headline, description, terms, urgency_hook, cta_primary,
                    cta_variants, value_proposition, prompt_used, provider,
                    status, context_json, created_at)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (
                    offer_id, business_id, strategy_request_id, platform, offer_type,
                    headline, description, terms, urgency_hook, cta_primary,
                    Json(cta_variants) if cta_variants else None,
                    value_proposition, prompt_used, provider, status,
                    Json(context_json) if context_json else None,
                    datetime.now(timezone.utc),
                ),
            )
        conn.commit()


def _persist_launch_package(
    pkg_id: str,
    business_id: str,
    recommendation_id: Optional[str],
    strategy_request_id: Optional[str],
    platform: Optional[str],
    landing_page_id: Optional[str],
    offer_id: Optional[str],
    package_json: dict,
    prompt_used: str,
    provider: str,
    status: str,
):
    _ensure_launch_packages_table()
    with _get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """INSERT INTO generated_launch_packages
                   (id, business_id, recommendation_id, strategy_request_id,
                    platform, landing_page_id, offer_id, package_json,
                    prompt_used, provider, status, created_at)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                (
                    pkg_id, business_id, recommendation_id, strategy_request_id,
                    platform, landing_page_id, offer_id,
                    Json(package_json), prompt_used, provider, status,
                    datetime.now(timezone.utc),
                ),
            )
        conn.commit()


def _query_landing_pages(business_id: str, limit: int) -> list[dict]:
    _ensure_landing_pages_table()
    with _get_connection() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                """SELECT id, business_id, strategy_request_id, platform, objective,
                          sections_json, full_html, meta_title, meta_description,
                          prompt_used, provider, status, context_json, created_at
                   FROM generated_landing_pages
                   WHERE business_id = %s ORDER BY created_at DESC LIMIT %s""",
                (business_id, limit),
            )
            return [dict(r) for r in cur.fetchall()]


def _query_offers(business_id: str, limit: int) -> list[dict]:
    _ensure_offers_table()
    with _get_connection() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                """SELECT id, business_id, strategy_request_id, platform, offer_type,
                          headline, description, terms, urgency_hook, cta_primary,
                          cta_variants, value_proposition, prompt_used, provider,
                          status, context_json, created_at
                   FROM generated_offers
                   WHERE business_id = %s ORDER BY created_at DESC LIMIT %s""",
                (business_id, limit),
            )
            return [dict(r) for r in cur.fetchall()]


def _query_launch_packages(business_id: str, limit: int) -> list[dict]:
    _ensure_launch_packages_table()
    with _get_connection() as conn:
        with conn.cursor(cursor_factory=RealDictCursor) as cur:
            cur.execute(
                """SELECT id, business_id, recommendation_id, strategy_request_id,
                          platform, landing_page_id, offer_id, package_json,
                          prompt_used, provider, status, created_at
                   FROM generated_launch_packages
                   WHERE business_id = %s ORDER BY created_at DESC LIMIT %s""",
                (business_id, limit),
            )
            return [dict(r) for r in cur.fetchall()]


# ---------------------------------------------------------------------------
# Prompt builders — Phase 2
# ---------------------------------------------------------------------------

class LandingPagePromptBuilder:
    """Builds system + user prompts for landing page generation."""

    SYSTEM_PROMPT = (
        "You are an expert conversion-rate-optimization copywriter and landing page architect. "
        "You create high-converting landing pages for paid advertising campaigns. "
        "You output ONLY valid JSON — no markdown, no code fences, no commentary. "
        "The JSON must follow this exact structure:\n"
        "{\n"
        '  "metaTitle": "string (under 60 chars)",\n'
        '  "metaDescription": "string (under 160 chars)",\n'
        '  "sections": [\n'
        "    {\n"
        '      "sectionType": "hero|features|social-proof|offer|faq|final-cta",\n'
        '      "headline": "string",\n'
        '      "body": "string",\n'
        '      "ctaText": "string or null",\n'
        '      "ctaUrl": "string or null"\n'
        "    }\n"
        "  ]\n"
        "}\n"
        "Generate exactly these sections in order: hero, features, social-proof, offer, faq, final-cta. "
        "Each section must be compelling, concise, and conversion-focused."
    )

    @classmethod
    def build_user_prompt(
        cls,
        business_info: Optional[dict],
        req: "LandingPageRequest",
    ) -> str:
        parts: list[str] = []

        if business_info:
            parts.append(f"Business: {business_info.get('business_name', 'N/A')}")
            if business_info.get("industry"):
                parts.append(f"Industry: {business_info['industry']}")
            if business_info.get("product"):
                parts.append(f"Product: {business_info['product']}")
            if business_info.get("target_audience"):
                parts.append(f"Target audience: {business_info['target_audience']}")

        if req.productFocus:
            parts.append(f"Product focus: {req.productFocus}")
        if req.targetAudience:
            parts.append(f"Target audience: {req.targetAudience}")
        if req.objective:
            parts.append(f"Campaign objective: {req.objective}")
        if req.platform:
            parts.append(f"Traffic source: {req.platform}")
        if req.tone:
            parts.append(f"Brand tone: {req.tone}")
        if req.offerHeadline:
            parts.append(f"Offer: {req.offerHeadline}")

        if req.landingPageRecommendations:
            recs = req.landingPageRecommendations
            if recs.get("pageTarget"):
                parts.append(f"Page target: {recs['pageTarget']}")
            if recs.get("conversionElements"):
                parts.append(f"Conversion elements: {json.dumps(recs['conversionElements'])}")
            if recs.get("messagingAngle"):
                parts.append(f"Messaging angle: {recs['messagingAngle']}")

        if req.offerStrategy:
            os_data = req.offerStrategy
            if os_data.get("promotion"):
                parts.append(f"Promotion: {os_data['promotion']}")
            if os_data.get("offer"):
                parts.append(f"Offer detail: {os_data['offer']}")

        if req.creativesNeeded:
            hooks = [c.get("hook") or c.get("headline") for c in req.creativesNeeded if c.get("hook") or c.get("headline")]
            if hooks:
                parts.append(f"Ad hooks being used: {', '.join(hooks[:5])}")

        return " | ".join(parts) if parts else "Create a generic high-converting landing page for a product."


class OfferPromptBuilder:
    """Builds system + user prompts for offer/promotion generation."""

    SYSTEM_PROMPT = (
        "You are an expert direct-response marketer specializing in promotional offers and "
        "conversion-focused copy. You create irresistible offers that drive action. "
        "You output ONLY valid JSON — no markdown, no code fences, no commentary. "
        "The JSON must follow this exact structure:\n"
        "{\n"
        '  "headline": "string — attention-grabbing offer headline",\n'
        '  "description": "string — 2-3 sentence offer description",\n'
        '  "terms": "string — clear offer terms/conditions",\n'
        '  "urgencyHook": "string — time-pressure or scarcity element",\n'
        '  "ctaPrimary": "string — main call-to-action text",\n'
        '  "ctaVariants": ["string — CTA variant 1", "string — CTA variant 2", "string — CTA variant 3"],\n'
        '  "valueProposition": "string — why this offer is compelling",\n'
        '  "emailSubjectLine": "string — subject line if offer is emailed",\n'
        '  "adCopySnippet": "string — 1-2 sentence ad copy mentioning the offer"\n'
        "}\n"
    )

    OFFER_TYPE_GUIDANCE = {
        "discount": "Focus on percentage or dollar-off savings. Emphasize the value gap between regular and sale price.",
        "free-trial": "Emphasize risk-free exploration. Highlight what they'll experience during the trial period.",
        "free-shipping": "Position free shipping as removing the last barrier to purchase. Works best with a minimum order.",
        "bundle": "Show the combined value vs. individual prices. Emphasize convenience and completeness.",
        "limited-time": "Create genuine urgency with a specific deadline. Emphasize what they'll miss.",
        "lead-magnet": "Offer high-perceived-value content in exchange for contact info. Emphasize immediate access.",
        "consultation": "Position as personalized expert advice. Emphasize the value of tailored recommendations.",
        "demo": "Highlight the hands-on experience. Show them exactly what they'll see/learn in the demo.",
    }

    @classmethod
    def build_user_prompt(
        cls,
        business_info: Optional[dict],
        req: "OfferRequest",
    ) -> str:
        parts: list[str] = []

        if business_info:
            parts.append(f"Business: {business_info.get('business_name', 'N/A')}")
            if business_info.get("industry"):
                parts.append(f"Industry: {business_info['industry']}")
            if business_info.get("product"):
                parts.append(f"Product: {business_info['product']}")
            if business_info.get("target_audience"):
                parts.append(f"Target audience: {business_info['target_audience']}")
            if business_info.get("price_range"):
                parts.append(f"Price range: {business_info['price_range']}")

        if req.productFocus:
            parts.append(f"Product focus: {req.productFocus}")
        if req.targetAudience:
            parts.append(f"Target audience: {req.targetAudience}")
        if req.objective:
            parts.append(f"Campaign objective: {req.objective}")
        if req.platform:
            parts.append(f"Platform: {req.platform}")
        if req.tone:
            parts.append(f"Brand tone: {req.tone}")
        if req.offerType:
            parts.append(f"Offer type: {req.offerType}")
            guidance = cls.OFFER_TYPE_GUIDANCE.get(req.offerType)
            if guidance:
                parts.append(f"Offer guidance: {guidance}")

        if req.offerStrategy:
            os_data = req.offerStrategy
            if os_data.get("promotion"):
                parts.append(f"Strategy promotion: {os_data['promotion']}")
            if os_data.get("offer"):
                parts.append(f"Strategy offer: {os_data['offer']}")
            if os_data.get("cta"):
                parts.append(f"Strategy CTA: {os_data['cta']}")

        if req.customerPersona:
            persona = req.customerPersona
            if persona.get("painPoints"):
                parts.append(f"Customer pain points: {json.dumps(persona['painPoints'])}")
            if persona.get("motivations"):
                parts.append(f"Customer motivations: {json.dumps(persona['motivations'])}")
            if persona.get("objections"):
                parts.append(f"Customer objections: {json.dumps(persona['objections'])}")

        return " | ".join(parts) if parts else "Create a compelling promotional offer for a product."


class LaunchPackagePromptBuilder:
    """Builds system + user prompts for enhanced launch package generation."""

    SYSTEM_PROMPT = (
        "You are a senior paid media strategist creating a comprehensive campaign launch package. "
        "You output ONLY valid JSON — no markdown, no code fences, no commentary. "
        "The JSON must follow this exact structure:\n"
        "{\n"
        '  "campaignBrief": "string — 3-5 sentence campaign overview",\n'
        '  "audienceStrategy": "string — targeting approach",\n'
        '  "budgetAllocation": {\n'
        '    "dailyBudget": "string",\n'
        '    "testingPhase": "string",\n'
        '    "scalingTrigger": "string"\n'
        "  },\n"
        '  "creativeRotation": [\n'
        '    {"slot": "string", "description": "string", "priority": "string"}\n'
        "  ],\n"
        '  "launchTimeline": [\n'
        '    {"day": "string", "action": "string"}\n'
        "  ],\n"
        '  "kpiTargets": {\n'
        '    "primaryKpi": "string",\n'
        '    "target": "string",\n'
        '    "secondaryKpis": ["string"]\n'
        "  },\n"
        '  "optimizationPlaybook": [\n'
        '    {"trigger": "string", "action": "string"}\n'
        "  ]\n"
        "}\n"
    )

    @classmethod
    def build_user_prompt(
        cls,
        business_info: Optional[dict],
        recommendation: Optional[dict],
        req: "EnhancedLaunchPackageRequest",
    ) -> str:
        parts: list[str] = []

        if business_info:
            parts.append(f"Business: {business_info.get('business_name', 'N/A')}")
            if business_info.get("industry"):
                parts.append(f"Industry: {business_info['industry']}")
            if business_info.get("product"):
                parts.append(f"Product: {business_info['product']}")
            if business_info.get("target_audience"):
                parts.append(f"Target audience: {business_info['target_audience']}")

        if recommendation:
            parts.append(f"Recommendation type: {recommendation.get('recommendation_type', 'N/A')}")
            if recommendation.get("title"):
                parts.append(f"Recommendation: {recommendation['title']}")
            if recommendation.get("description"):
                parts.append(f"Context: {recommendation['description']}")
            if recommendation.get("priority"):
                parts.append(f"Priority: {recommendation['priority']}")

        if req.platform:
            parts.append(f"Platform: {req.platform}")
        if req.landingPageRecommendations:
            parts.append(f"Landing page strategy: {json.dumps(req.landingPageRecommendations)}")
        if req.offerStrategy:
            parts.append(f"Offer strategy: {json.dumps(req.offerStrategy)}")
        if req.customerPersona:
            parts.append(f"Customer persona: {json.dumps(req.customerPersona)}")
        if req.creativesNeeded:
            parts.append(f"Creatives planned: {len(req.creativesNeeded)} assets")

        return " | ".join(parts) if parts else "Create a campaign launch package."


# ---------------------------------------------------------------------------
# Stubbed response builders — Phase 2
# ---------------------------------------------------------------------------

def _stub_landing_page(business_info: Optional[dict], req: "LandingPageRequest") -> dict:
    """Generate a deterministic stubbed landing page when no API key is configured."""
    biz_name = (business_info or {}).get("business_name", "Your Business")
    product = req.productFocus or (business_info or {}).get("product", "our product")
    audience = req.targetAudience or (business_info or {}).get("target_audience", "customers")
    offer = req.offerHeadline or "Special Offer"

    return {
        "metaTitle": f"{biz_name} — {product} | {offer}",
        "metaDescription": f"Discover {product} from {biz_name}. {offer}. Perfect for {audience}.",
        "sections": [
            {
                "sectionType": "hero",
                "headline": f"Transform Your Experience with {product}",
                "body": f"Join thousands of {audience} who already trust {biz_name}. {offer} — available now.",
                "ctaText": "Get Started",
                "ctaUrl": "#signup",
            },
            {
                "sectionType": "features",
                "headline": f"Why {product} Stands Out",
                "body": "Premium quality. Exceptional results. Backed by real customer success stories.",
                "ctaText": None,
                "ctaUrl": None,
            },
            {
                "sectionType": "social-proof",
                "headline": "Trusted by Thousands",
                "body": f'"Best purchase I\'ve made this year." — Happy {biz_name} Customer',
                "ctaText": None,
                "ctaUrl": None,
            },
            {
                "sectionType": "offer",
                "headline": offer,
                "body": f"For a limited time, get {product} at an exclusive price. Don't miss out.",
                "ctaText": "Claim Your Offer",
                "ctaUrl": "#offer",
            },
            {
                "sectionType": "faq",
                "headline": "Frequently Asked Questions",
                "body": "Q: How quickly will I see results? A: Most customers see results within the first week.",
                "ctaText": None,
                "ctaUrl": None,
            },
            {
                "sectionType": "final-cta",
                "headline": "Ready to Get Started?",
                "body": f"Join {audience} who chose {biz_name}. Your transformation starts today.",
                "ctaText": "Start Now",
                "ctaUrl": "#signup",
            },
        ],
    }


def _stub_offer(business_info: Optional[dict], req: "OfferRequest") -> dict:
    """Generate a deterministic stubbed offer when no API key is configured."""
    product = req.productFocus or (business_info or {}).get("product", "our product")
    offer_type = req.offerType or "limited-time"

    offer_templates = {
        "discount": {"headline": f"Save 20% on {product} Today", "urgency": "Offer expires in 48 hours"},
        "free-trial": {"headline": f"Try {product} Free for 14 Days", "urgency": "Limited spots available this month"},
        "free-shipping": {"headline": f"Free Shipping on {product}", "urgency": "Free shipping ends Sunday"},
        "bundle": {"headline": f"Complete {product} Bundle — Save 30%", "urgency": "Bundle deal ends this week"},
        "limited-time": {"headline": f"Limited Time: Special {product} Offer", "urgency": "Only 3 days left"},
        "lead-magnet": {"headline": f"Free Guide: Get the Most from {product}", "urgency": "Download now — instant access"},
        "consultation": {"headline": f"Free {product} Strategy Session", "urgency": "Only 10 sessions available this month"},
        "demo": {"headline": f"See {product} in Action — Free Demo", "urgency": "Book your spot today"},
    }
    template = offer_templates.get(offer_type, offer_templates["limited-time"])

    return {
        "headline": template["headline"],
        "description": f"Discover why customers love {product}. This exclusive offer won't last long.",
        "terms": "Valid for new customers. One per household. Cannot be combined with other offers.",
        "urgencyHook": template["urgency"],
        "ctaPrimary": "Claim Your Offer",
        "ctaVariants": ["Get Started Now", "Yes, I Want This", "Unlock My Deal"],
        "valueProposition": f"Get {product} at the best value we've ever offered.",
        "emailSubjectLine": f"🎉 {template['headline']}",
        "adCopySnippet": f"{template['headline']}. {template['urgency']}. Don't miss this.",
    }


def _stub_launch_package(business_info: Optional[dict], recommendation: Optional[dict]) -> dict:
    """Generate a deterministic stubbed launch package when no API key is configured."""
    rec_type = (recommendation or {}).get("recommendation_type", "SCALE")
    product = (business_info or {}).get("product", "product")

    return {
        "campaignBrief": f"Launch campaign for {product} based on {rec_type} recommendation. "
                         f"Focus on proven creative elements with controlled scaling approach.",
        "audienceStrategy": "Start with existing high-converting audiences. Expand to lookalikes after 72h validation.",
        "budgetAllocation": {
            "dailyBudget": "$20-50/day initial",
            "testingPhase": "3-5 days at low spend to validate creative performance",
            "scalingTrigger": "Scale when ROAS > 2.0 for 3 consecutive days",
        },
        "creativeRotation": [
            {"slot": "Primary", "description": "Top-performing creative variant", "priority": "HIGH"},
            {"slot": "Variation A", "description": "Hook variation of primary", "priority": "MEDIUM"},
            {"slot": "Variation B", "description": "Visual style variation", "priority": "MEDIUM"},
        ],
        "launchTimeline": [
            {"day": "Day 0", "action": "Upload creatives, configure targeting, set initial budget"},
            {"day": "Day 1-3", "action": "Monitor impressions and CTR. No changes unless critical issues."},
            {"day": "Day 4-7", "action": "Review early ROAS. Pause underperformers. Scale winners by 20%."},
            {"day": "Day 8-14", "action": "Full optimization cycle. Test new audience segments."},
        ],
        "kpiTargets": {
            "primaryKpi": "ROAS",
            "target": "> 2.0",
            "secondaryKpis": ["CTR > 1.5%", "CPA < $30", "Frequency < 3.0"],
        },
        "optimizationPlaybook": [
            {"trigger": "CTR < 0.8% after 1000 impressions", "action": "Swap creative or adjust hook"},
            {"trigger": "ROAS < 1.0 after 5 days", "action": "Pause and reassess targeting"},
            {"trigger": "Frequency > 3.5", "action": "Refresh creative or expand audience"},
            {"trigger": "ROAS > 3.0 for 3 days", "action": "Increase budget by 25%"},
        ],
    }


# ---------------------------------------------------------------------------
# Endpoints — Phase 2: Landing Page Generator
# ---------------------------------------------------------------------------

@app.post("/generate/landing-page")
def generate_landing_page(req: LandingPageRequest, request: Request):
    """Generate a high-converting landing page from business + strategy context."""
    request_id = request.state.request_id
    logger.info(
        "Generate landing page businessId=%s platform=%s",
        req.businessId, req.platform,
        extra={"request_id": request_id},
    )

    business_info = _fetch_business(req.businessId)
    if not business_info:
        return JSONResponse(status_code=400, content={
            "requestId": request_id,
            "error": "BAD_REQUEST",
            "message": f"Business not found: {req.businessId}",
            "details": {},
        })

    user_prompt = LandingPagePromptBuilder.build_user_prompt(business_info, req)

    page_id = str(uuid.uuid4())
    provider = "STUB"
    status = "STUBBED"
    page_data: dict = {}

    if _is_chat_configured():
        try:
            raw = _call_openai_chat(
                LandingPagePromptBuilder.SYSTEM_PROMPT,
                user_prompt,
                temperature=0.7,
            )
            page_data = json.loads(raw)
            provider = "OPENAI"
            status = "SUCCESS"
        except json.JSONDecodeError as e:
            logger.error("Failed to parse landing page JSON: %s", str(e), extra={"request_id": request_id})
            page_data = _stub_landing_page(business_info, req)
            provider = "OPENAI_PARSE_FALLBACK"
            status = "PARTIAL"
        except Exception as e:
            logger.error("OpenAI chat failed for landing page: %s", str(e), extra={"request_id": request_id})
            page_data = _stub_landing_page(business_info, req)
            status = "FAILED"
    else:
        page_data = _stub_landing_page(business_info, req)

    sections = page_data.get("sections", [])
    meta_title = page_data.get("metaTitle")
    meta_description = page_data.get("metaDescription")

    context_json = {}
    if req.landingPageRecommendations:
        context_json["landingPageRecommendations"] = req.landingPageRecommendations
    if req.offerStrategy:
        context_json["offerStrategy"] = req.offerStrategy

    _persist_landing_page(
        page_id=page_id,
        business_id=req.businessId,
        strategy_request_id=req.strategyRequestId,
        platform=req.platform,
        objective=req.objective,
        sections_json=sections,
        full_html=None,
        meta_title=meta_title,
        meta_description=meta_description,
        prompt_used=user_prompt,
        provider=provider,
        status=status,
        context_json=context_json if context_json else None,
    )

    logger.info("Landing page generated id=%s status=%s", page_id, status, extra={"request_id": request_id})

    return {
        "requestId": request_id,
        "landingPageId": page_id,
        "status": status,
        "provider": provider,
        "metaTitle": meta_title,
        "metaDescription": meta_description,
        "sections": sections,
        "promptUsed": user_prompt,
    }


@app.get("/generate/landing-pages")
def list_landing_pages(
    businessId: str = Query(...),
    limit: int = Query(default=20, ge=1, le=100),
    request: Request = None,
):
    """Return recent generated landing pages for a business."""
    rows = _query_landing_pages(businessId, limit)
    items = []
    for r in rows:
        items.append({
            "landingPageId": str(r["id"]),
            "businessId": str(r["business_id"]),
            "strategyRequestId": str(r["strategy_request_id"]) if r.get("strategy_request_id") else None,
            "platform": r.get("platform"),
            "objective": r.get("objective"),
            "metaTitle": r.get("meta_title"),
            "metaDescription": r.get("meta_description"),
            "sections": r.get("sections_json"),
            "provider": r["provider"],
            "status": r["status"],
            "createdAt": r["created_at"].isoformat() if r.get("created_at") else None,
        })
    return {"businessId": businessId, "landingPages": items, "count": len(items)}


# ---------------------------------------------------------------------------
# Endpoints — Phase 2: Offer Generator
# ---------------------------------------------------------------------------

@app.post("/generate/offer")
def generate_offer(req: OfferRequest, request: Request):
    """Generate a promotional offer with copy variants from business + strategy context."""
    request_id = request.state.request_id
    logger.info(
        "Generate offer businessId=%s offerType=%s platform=%s",
        req.businessId, req.offerType, req.platform,
        extra={"request_id": request_id},
    )

    business_info = _fetch_business(req.businessId)
    if not business_info:
        return JSONResponse(status_code=400, content={
            "requestId": request_id,
            "error": "BAD_REQUEST",
            "message": f"Business not found: {req.businessId}",
            "details": {},
        })

    user_prompt = OfferPromptBuilder.build_user_prompt(business_info, req)

    offer_id = str(uuid.uuid4())
    provider = "STUB"
    status = "STUBBED"
    offer_data: dict = {}

    if _is_chat_configured():
        try:
            raw = _call_openai_chat(
                OfferPromptBuilder.SYSTEM_PROMPT,
                user_prompt,
                temperature=0.8,
            )
            offer_data = json.loads(raw)
            provider = "OPENAI"
            status = "SUCCESS"
        except json.JSONDecodeError as e:
            logger.error("Failed to parse offer JSON: %s", str(e), extra={"request_id": request_id})
            offer_data = _stub_offer(business_info, req)
            provider = "OPENAI_PARSE_FALLBACK"
            status = "PARTIAL"
        except Exception as e:
            logger.error("OpenAI chat failed for offer: %s", str(e), extra={"request_id": request_id})
            offer_data = _stub_offer(business_info, req)
            status = "FAILED"
    else:
        offer_data = _stub_offer(business_info, req)

    context_json = {}
    if req.offerStrategy:
        context_json["offerStrategy"] = req.offerStrategy
    if req.customerPersona:
        context_json["customerPersona"] = req.customerPersona

    _persist_offer(
        offer_id=offer_id,
        business_id=req.businessId,
        strategy_request_id=req.strategyRequestId,
        platform=req.platform,
        offer_type=req.offerType,
        headline=offer_data.get("headline", ""),
        description=offer_data.get("description", ""),
        terms=offer_data.get("terms"),
        urgency_hook=offer_data.get("urgencyHook"),
        cta_primary=offer_data.get("ctaPrimary", "Learn More"),
        cta_variants=offer_data.get("ctaVariants"),
        value_proposition=offer_data.get("valueProposition"),
        prompt_used=user_prompt,
        provider=provider,
        status=status,
        context_json=context_json if context_json else None,
    )

    logger.info("Offer generated id=%s status=%s", offer_id, status, extra={"request_id": request_id})

    return {
        "requestId": request_id,
        "offerId": offer_id,
        "status": status,
        "provider": provider,
        "headline": offer_data.get("headline"),
        "description": offer_data.get("description"),
        "terms": offer_data.get("terms"),
        "urgencyHook": offer_data.get("urgencyHook"),
        "ctaPrimary": offer_data.get("ctaPrimary"),
        "ctaVariants": offer_data.get("ctaVariants"),
        "valueProposition": offer_data.get("valueProposition"),
        "emailSubjectLine": offer_data.get("emailSubjectLine"),
        "adCopySnippet": offer_data.get("adCopySnippet"),
        "promptUsed": user_prompt,
    }


@app.get("/generate/offers")
def list_offers(
    businessId: str = Query(...),
    limit: int = Query(default=20, ge=1, le=100),
    request: Request = None,
):
    """Return recent generated offers for a business."""
    rows = _query_offers(businessId, limit)
    items = []
    for r in rows:
        items.append({
            "offerId": str(r["id"]),
            "businessId": str(r["business_id"]),
            "strategyRequestId": str(r["strategy_request_id"]) if r.get("strategy_request_id") else None,
            "platform": r.get("platform"),
            "offerType": r.get("offer_type"),
            "headline": r["headline"],
            "description": r["description"],
            "terms": r.get("terms"),
            "urgencyHook": r.get("urgency_hook"),
            "ctaPrimary": r["cta_primary"],
            "ctaVariants": r.get("cta_variants"),
            "valueProposition": r.get("value_proposition"),
            "provider": r["provider"],
            "status": r["status"],
            "createdAt": r["created_at"].isoformat() if r.get("created_at") else None,
        })
    return {"businessId": businessId, "offers": items, "count": len(items)}


# ---------------------------------------------------------------------------
# Endpoints — Phase 2: Enhanced Launch Package
# ---------------------------------------------------------------------------

@app.post("/generate/launch-package")
def generate_launch_package(req: EnhancedLaunchPackageRequest, request: Request):
    """Generate an AI-enhanced launch package combining recommendation, landing page, and offer."""
    request_id = request.state.request_id
    logger.info(
        "Generate enhanced launch package businessId=%s recommendationId=%s",
        req.businessId, req.recommendationId,
        extra={"request_id": request_id},
    )

    business_info = _fetch_business(req.businessId)
    if not business_info:
        return JSONResponse(status_code=400, content={
            "requestId": request_id,
            "error": "BAD_REQUEST",
            "message": f"Business not found: {req.businessId}",
            "details": {},
        })

    # Fetch recommendation context
    recommendation = _query_recommendation(req.recommendationId)
    if not recommendation:
        return JSONResponse(status_code=404, content={
            "requestId": request_id,
            "error": "NOT_FOUND",
            "message": f"Recommendation not found: {req.recommendationId}",
            "details": {},
        })

    # Verify business ownership
    if str(recommendation["business_id"]) != req.businessId:
        return JSONResponse(status_code=400, content={
            "requestId": request_id,
            "error": "BAD_REQUEST",
            "message": "Recommendation does not belong to the specified business",
            "details": {},
        })

    # Determine platform from recommendation or request
    platform = req.platform
    if not platform:
        desc = (recommendation.get("description") or "").lower()
        title = (recommendation.get("title") or "").lower()
        for p in ["tiktok", "google", "youtube", "meta"]:
            if p in desc or p in title:
                platform = p
                break
        if not platform:
            platform = "meta"

    # --- Step 1: Generate landing page ---
    lp_req = LandingPageRequest(
        businessId=req.businessId,
        strategyRequestId=req.strategyRequestId,
        platform=platform,
        objective=recommendation.get("recommendation_type"),
        productFocus=(business_info or {}).get("product"),
        targetAudience=(business_info or {}).get("target_audience"),
        landingPageRecommendations=req.landingPageRecommendations,
        offerStrategy=req.offerStrategy,
        creativesNeeded=req.creativesNeeded,
    )
    lp_user_prompt = LandingPagePromptBuilder.build_user_prompt(business_info, lp_req)
    landing_page_id = str(uuid.uuid4())
    lp_provider = "STUB"
    lp_status = "STUBBED"
    lp_data: dict = {}

    if _is_chat_configured():
        try:
            raw = _call_openai_chat(LandingPagePromptBuilder.SYSTEM_PROMPT, lp_user_prompt, temperature=0.7)
            lp_data = json.loads(raw)
            lp_provider = "OPENAI"
            lp_status = "SUCCESS"
        except json.JSONDecodeError:
            lp_data = _stub_landing_page(business_info, lp_req)
            lp_provider = "OPENAI_PARSE_FALLBACK"
            lp_status = "PARTIAL"
        except Exception:
            lp_data = _stub_landing_page(business_info, lp_req)
            lp_status = "FAILED"
    else:
        lp_data = _stub_landing_page(business_info, lp_req)

    _persist_landing_page(
        page_id=landing_page_id,
        business_id=req.businessId,
        strategy_request_id=req.strategyRequestId,
        platform=platform,
        objective=recommendation.get("recommendation_type"),
        sections_json=lp_data.get("sections", []),
        full_html=None,
        meta_title=lp_data.get("metaTitle"),
        meta_description=lp_data.get("metaDescription"),
        prompt_used=lp_user_prompt,
        provider=lp_provider,
        status=lp_status,
        context_json=None,
    )

    # --- Step 2: Generate offer ---
    offer_req = OfferRequest(
        businessId=req.businessId,
        strategyRequestId=req.strategyRequestId,
        platform=platform,
        productFocus=(business_info or {}).get("product"),
        targetAudience=(business_info or {}).get("target_audience"),
        offerStrategy=req.offerStrategy,
        customerPersona=req.customerPersona,
    )
    offer_user_prompt = OfferPromptBuilder.build_user_prompt(business_info, offer_req)
    offer_id = str(uuid.uuid4())
    offer_provider = "STUB"
    offer_status = "STUBBED"
    offer_data: dict = {}

    if _is_chat_configured():
        try:
            raw = _call_openai_chat(OfferPromptBuilder.SYSTEM_PROMPT, offer_user_prompt, temperature=0.8)
            offer_data = json.loads(raw)
            offer_provider = "OPENAI"
            offer_status = "SUCCESS"
        except json.JSONDecodeError:
            offer_data = _stub_offer(business_info, offer_req)
            offer_provider = "OPENAI_PARSE_FALLBACK"
            offer_status = "PARTIAL"
        except Exception:
            offer_data = _stub_offer(business_info, offer_req)
            offer_status = "FAILED"
    else:
        offer_data = _stub_offer(business_info, offer_req)

    _persist_offer(
        offer_id=offer_id,
        business_id=req.businessId,
        strategy_request_id=req.strategyRequestId,
        platform=platform,
        offer_type=None,
        headline=offer_data.get("headline", ""),
        description=offer_data.get("description", ""),
        terms=offer_data.get("terms"),
        urgency_hook=offer_data.get("urgencyHook"),
        cta_primary=offer_data.get("ctaPrimary", "Learn More"),
        cta_variants=offer_data.get("ctaVariants"),
        value_proposition=offer_data.get("valueProposition"),
        prompt_used=offer_user_prompt,
        provider=offer_provider,
        status=offer_status,
        context_json=None,
    )

    # --- Step 3: Generate campaign strategy ---
    strategy_prompt = LaunchPackagePromptBuilder.build_user_prompt(business_info, recommendation, req)
    pkg_id = str(uuid.uuid4())
    pkg_provider = "STUB"
    pkg_status = "STUBBED"
    strategy_data: dict = {}

    if _is_chat_configured():
        try:
            raw = _call_openai_chat(LaunchPackagePromptBuilder.SYSTEM_PROMPT, strategy_prompt, temperature=0.7)
            strategy_data = json.loads(raw)
            pkg_provider = "OPENAI"
            pkg_status = "SUCCESS"
        except json.JSONDecodeError:
            strategy_data = _stub_launch_package(business_info, recommendation)
            pkg_provider = "OPENAI_PARSE_FALLBACK"
            pkg_status = "PARTIAL"
        except Exception:
            strategy_data = _stub_launch_package(business_info, recommendation)
            pkg_status = "FAILED"
    else:
        strategy_data = _stub_launch_package(business_info, recommendation)

    # Combine into final package
    full_package = {
        "landingPage": {
            "landingPageId": landing_page_id,
            "status": lp_status,
            "metaTitle": lp_data.get("metaTitle"),
            "metaDescription": lp_data.get("metaDescription"),
            "sections": lp_data.get("sections", []),
        },
        "offer": {
            "offerId": offer_id,
            "status": offer_status,
            "headline": offer_data.get("headline"),
            "description": offer_data.get("description"),
            "terms": offer_data.get("terms"),
            "urgencyHook": offer_data.get("urgencyHook"),
            "ctaPrimary": offer_data.get("ctaPrimary"),
            "ctaVariants": offer_data.get("ctaVariants"),
            "valueProposition": offer_data.get("valueProposition"),
            "emailSubjectLine": offer_data.get("emailSubjectLine"),
            "adCopySnippet": offer_data.get("adCopySnippet"),
        },
        "campaignStrategy": strategy_data,
        "recommendation": {
            "recommendationId": req.recommendationId,
            "type": recommendation.get("recommendation_type"),
            "title": recommendation.get("title"),
            "priority": recommendation.get("priority"),
        },
    }

    _persist_launch_package(
        pkg_id=pkg_id,
        business_id=req.businessId,
        recommendation_id=req.recommendationId,
        strategy_request_id=req.strategyRequestId,
        platform=platform,
        landing_page_id=landing_page_id,
        offer_id=offer_id,
        package_json=full_package,
        prompt_used=strategy_prompt,
        provider=pkg_provider,
        status=pkg_status,
    )

    logger.info(
        "Enhanced launch package generated id=%s lp=%s offer=%s status=%s",
        pkg_id, landing_page_id, offer_id, pkg_status,
        extra={"request_id": request_id},
    )

    return {
        "requestId": request_id,
        "packageId": pkg_id,
        "status": pkg_status,
        "platform": platform,
        "landingPage": full_package["landingPage"],
        "offer": full_package["offer"],
        "campaignStrategy": full_package["campaignStrategy"],
        "recommendation": full_package["recommendation"],
    }


@app.get("/generate/launch-packages")
def list_launch_packages(
    businessId: str = Query(...),
    limit: int = Query(default=20, ge=1, le=100),
    request: Request = None,
):
    """Return recent generated launch packages for a business."""
    rows = _query_launch_packages(businessId, limit)
    items = []
    for r in rows:
        items.append({
            "packageId": str(r["id"]),
            "businessId": str(r["business_id"]),
            "recommendationId": str(r["recommendation_id"]) if r.get("recommendation_id") else None,
            "strategyRequestId": str(r["strategy_request_id"]) if r.get("strategy_request_id") else None,
            "platform": r.get("platform"),
            "landingPageId": str(r["landing_page_id"]) if r.get("landing_page_id") else None,
            "offerId": str(r["offer_id"]) if r.get("offer_id") else None,
            "package": r.get("package_json"),
            "provider": r["provider"],
            "status": r["status"],
            "createdAt": r["created_at"].isoformat() if r.get("created_at") else None,
        })
    return {"businessId": businessId, "launchPackages": items, "count": len(items)}
