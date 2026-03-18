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
