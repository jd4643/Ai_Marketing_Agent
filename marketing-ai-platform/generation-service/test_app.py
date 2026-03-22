import json
import unittest
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient

from app import (
    app,
    CreativeAssetPromptBuilder,
    TrendContext,
    AssetMetadata,
    LandingPagePromptBuilder,
    OfferPromptBuilder,
    LaunchPackagePromptBuilder,
    LandingPageRequest,
    OfferRequest,
    EnhancedLaunchPackageRequest,
)


client = TestClient(app)


class TestHealthEndpoint(unittest.TestCase):
    def test_health(self):
        resp = client.get("/health")
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["status"], "UP")


class TestCreativeAssetValidation(unittest.TestCase):
    def test_missing_business_id(self):
        resp = client.post("/generate/creative-assets", json={
            "assetType": "image"
        })
        self.assertEqual(resp.status_code, 422)

    def test_invalid_asset_type(self):
        resp = client.post("/generate/creative-assets", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "assetType": "podcast"
        })
        self.assertEqual(resp.status_code, 422)

    def test_invalid_platform(self):
        resp = client.post("/generate/creative-assets", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "assetType": "image",
            "platform": "snapchat"
        })
        self.assertEqual(resp.status_code, 422)

    def test_count_exceeds_max(self):
        resp = client.post("/generate/creative-assets", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "assetType": "image",
            "count": 10
        })
        self.assertEqual(resp.status_code, 422)


class TestPromptBuilder(unittest.TestCase):
    def test_direct_prompt_passthrough(self):
        result = CreativeAssetPromptBuilder.build(
            asset_type="image",
            platform=None,
            direct_prompt="A beautiful product shot",
            metadata=None,
            trend_context=None,
            business_info=None,
        )
        self.assertEqual(result, "A beautiful product shot")

    def test_prompt_includes_scene_and_style(self):
        meta = AssetMetadata(
            sceneDescription="Product on marble table",
            visualStyle="minimal",
            lightingNotes="Soft studio",
            compositionNotes="Overhead flat lay",
            emotionalAngle="aspirational",
            brandTone="premium",
            productFocus="gold necklace",
        )
        result = CreativeAssetPromptBuilder.build(
            asset_type="image",
            platform="meta",
            direct_prompt=None,
            metadata=meta,
            trend_context=None,
            business_info={"industry": "jewelry", "product": "gold necklace"},
        )
        self.assertIn("Product on marble table", result)
        self.assertIn("minimal", result)
        self.assertIn("Soft studio", result)
        self.assertIn("Overhead flat lay", result)
        self.assertIn("aspirational", result)
        self.assertIn("premium", result)
        self.assertIn("gold necklace", result)
        self.assertIn("Meta", result)

    def test_prompt_includes_trends(self):
        trend = TrendContext(industry="jewelry", keywords=["minimalist", "sustainable"])
        result = CreativeAssetPromptBuilder.build(
            asset_type="image",
            platform=None,
            direct_prompt=None,
            metadata=AssetMetadata(conceptName="Test Concept"),
            trend_context=trend,
            business_info=None,
        )
        self.assertIn("minimalist", result)
        self.assertIn("sustainable", result)

    def test_prompt_includes_platform_hint(self):
        result = CreativeAssetPromptBuilder.build(
            asset_type="video",
            platform="tiktok",
            direct_prompt=None,
            metadata=AssetMetadata(conceptName="TikTok Test"),
            trend_context=None,
            business_info=None,
        )
        self.assertIn("TikTok", result)
        self.assertIn("video", result.lower())

    def test_prompt_falls_back_to_default(self):
        result = CreativeAssetPromptBuilder.build(
            asset_type="image",
            platform=None,
            direct_prompt=None,
            metadata=None,
            trend_context=None,
            business_info=None,
        )
        self.assertIn("Professional", result)

    def test_prompt_with_business_context(self):
        result = CreativeAssetPromptBuilder.build(
            asset_type="image",
            platform=None,
            direct_prompt=None,
            metadata=AssetMetadata(conceptName="Biz Test"),
            trend_context=None,
            business_info={"industry": "fashion", "product": "leather bag"},
        )
        self.assertIn("leather bag", result)
        self.assertIn("fashion", result)


class TestStubbedGeneration(unittest.TestCase):
    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._is_provider_configured", return_value=False)
    def test_stubbed_response(self, mock_provider, mock_persist, mock_biz):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        resp = client.post("/generate/creative-assets", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "assetType": "image",
            "platform": "meta",
            "prompt": "beautiful ring in studio setting",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "STUBBED")
        self.assertIn("assets", data)
        self.assertEqual(len(data["assets"]), 1)
        asset = data["assets"][0]
        self.assertEqual(asset["assetType"], "image")
        self.assertEqual(asset["status"], "STUBBED")
        self.assertEqual(asset["provider"], "STUB")
        self.assertIsNotNone(asset["assetId"])
        self.assertIsNotNone(data["requestId"])
        mock_persist.assert_called_once()

    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._is_provider_configured", return_value=False)
    def test_stubbed_with_metadata(self, mock_provider, mock_persist, mock_biz):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        resp = client.post("/generate/creative-assets", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "assetType": "image",
            "platform": "meta",
            "creativeConceptName": "UGC Lifestyle",
            "metadata": {
                "hook": "Stop scrolling",
                "headline": "Try it now",
                "cta": "Shop Now",
                "visualStyle": "UGC",
                "sceneDescription": "Product unboxing scene",
                "lightingNotes": "Natural daylight",
            },
            "trendContext": {
                "industry": "jewelry",
                "keywords": ["minimalist", "sustainable"],
            },
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "STUBBED")
        asset = data["assets"][0]
        self.assertIn("minimalist", asset["promptUsed"])
        self.assertIn("Product unboxing scene", asset["promptUsed"])
        mock_persist.assert_called_once()

    @patch("app._fetch_business")
    def test_business_not_found(self, mock_biz):
        mock_biz.return_value = None
        resp = client.post("/generate/creative-assets", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "assetType": "image",
        })
        self.assertEqual(resp.status_code, 400)
        self.assertIn("Business not found", resp.json()["message"])


class TestListAssets(unittest.TestCase):
    @patch("app._query_assets")
    def test_list_assets(self, mock_query):
        from datetime import datetime, timezone
        mock_query.return_value = [
            {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "business_id": "660e8400-e29b-41d4-a716-446655440000",
                "creative_id": None,
                "strategy_request_id": None,
                "asset_type": "image",
                "platform": "meta",
                "prompt_text": "test prompt",
                "provider": "STUB",
                "provider_asset_id": None,
                "asset_url": None,
                "thumbnail_url": None,
                "status": "STUBBED",
                "trend_context_json": None,
                "metadata_json": None,
                "created_at": datetime(2026, 1, 1, tzinfo=timezone.utc),
            }
        ]
        resp = client.get("/generate/assets?businessId=660e8400-e29b-41d4-a716-446655440000")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["count"], 1)
        self.assertEqual(data["assets"][0]["assetType"], "image")
        self.assertEqual(data["assets"][0]["status"], "STUBBED")


class TestGetAsset(unittest.TestCase):
    @patch("app._query_asset_by_id")
    def test_get_asset_found(self, mock_query):
        from datetime import datetime, timezone
        mock_query.return_value = {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "business_id": "660e8400-e29b-41d4-a716-446655440000",
            "creative_id": None,
            "strategy_request_id": None,
            "asset_type": "image",
            "platform": "meta",
            "prompt_text": "test prompt",
            "provider": "STUB",
            "provider_asset_id": None,
            "asset_url": None,
            "thumbnail_url": None,
            "status": "STUBBED",
            "trend_context_json": None,
            "metadata_json": None,
            "created_at": datetime(2026, 1, 1, tzinfo=timezone.utc),
        }
        resp = client.get("/generate/assets/550e8400-e29b-41d4-a716-446655440000")
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["assetId"], "550e8400-e29b-41d4-a716-446655440000")

    @patch("app._query_asset_by_id")
    def test_get_asset_not_found(self, mock_query):
        mock_query.return_value = None
        resp = client.get("/generate/assets/550e8400-e29b-41d4-a716-446655440000")
        self.assertEqual(resp.status_code, 404)


class TestLegacyEndpoint(unittest.TestCase):
    def test_legacy_generate_image(self):
        resp = client.post("/generate/image", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "prompt": "test",
        })
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["status"], "STUBBED")


class TestFromWinnerValidation(unittest.TestCase):
    def test_missing_winner_asset_id(self):
        resp = client.post("/generate/creative-assets/from-winner", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 422)

    def test_invalid_variation_type(self):
        resp = client.post("/generate/creative-assets/from-winner", json={
            "winnerAssetId": "550e8400-e29b-41d4-a716-446655440000",
            "businessId": "660e8400-e29b-41d4-a716-446655440000",
            "variationType": "invalid",
        })
        self.assertEqual(resp.status_code, 422)


class TestFromWinnerEndpoint(unittest.TestCase):
    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._query_winner_asset")
    @patch("app._is_provider_configured", return_value=False)
    def test_stubbed_from_winner(self, mock_provider, mock_winner, mock_persist, mock_biz):
        winner_id = "550e8400-e29b-41d4-a716-446655440000"
        business_id = "660e8400-e29b-41d4-a716-446655440000"
        mock_winner.return_value = {
            "id": winner_id,
            "business_id": business_id,
            "asset_type": "image",
            "platform": "meta",
            "prompt_text": "original winning prompt",
            "metadata_json": {"hook": "Stop scrolling", "visualStyle": "UGC", "emotionalAngle": "curiosity"},
            "trend_context_json": None,
            "total_impressions": 5000,
            "total_clicks": 200,
            "total_conversions": 10,
            "avg_roas": 3.5,
        }
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        resp = client.post("/generate/creative-assets/from-winner", json={
            "winnerAssetId": winner_id,
            "businessId": business_id,
            "variationType": "iteration",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "STUBBED")
        self.assertEqual(data["winnerAssetId"], winner_id)
        self.assertEqual(data["variationType"], "iteration")
        self.assertEqual(len(data["assets"]), 1)
        asset = data["assets"][0]
        self.assertEqual(asset["basedOnWinnerId"], winner_id)
        self.assertIn("winning", asset["promptUsed"].lower())
        mock_persist.assert_called_once()

    @patch("app._query_winner_asset")
    def test_winner_not_found(self, mock_winner):
        mock_winner.return_value = None
        resp = client.post("/generate/creative-assets/from-winner", json={
            "winnerAssetId": "550e8400-e29b-41d4-a716-446655440000",
            "businessId": "660e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 404)

    @patch("app._query_winner_asset")
    def test_business_mismatch(self, mock_winner):
        mock_winner.return_value = {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "business_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "asset_type": "image",
            "platform": "meta",
            "prompt_text": "test",
            "metadata_json": None,
            "trend_context_json": None,
            "total_impressions": 0,
            "total_clicks": 0,
            "total_conversions": 0,
            "avg_roas": 0,
        }
        resp = client.post("/generate/creative-assets/from-winner", json={
            "winnerAssetId": "550e8400-e29b-41d4-a716-446655440000",
            "businessId": "660e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 400)

    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._query_winner_asset")
    @patch("app._is_provider_configured", return_value=False)
    def test_remix_variation(self, mock_provider, mock_winner, mock_persist, mock_biz):
        winner_id = "550e8400-e29b-41d4-a716-446655440000"
        business_id = "660e8400-e29b-41d4-a716-446655440000"
        mock_winner.return_value = {
            "id": winner_id,
            "business_id": business_id,
            "asset_type": "image",
            "platform": "meta",
            "prompt_text": "original prompt",
            "metadata_json": {"hook": "Try this", "visualStyle": "minimal"},
            "trend_context_json": None,
            "total_impressions": 3000,
            "total_clicks": 150,
            "total_conversions": 5,
            "avg_roas": 2.5,
        }
        mock_biz.return_value = {"business_name": "TestBiz", "industry": "fashion", "product": "bags"}
        resp = client.post("/generate/creative-assets/from-winner", json={
            "winnerAssetId": winner_id,
            "businessId": business_id,
            "variationType": "remix",
            "count": 2,
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(len(data["assets"]), 2)
        self.assertEqual(data["variationType"], "remix")
        self.assertIn("remix", data["assets"][0]["promptUsed"].lower())


class TestVariationPromptBuilder(unittest.TestCase):
    def test_iteration_prompt(self):
        from app import _build_variation_prompt
        winner = {
            "prompt_text": "original product shot",
            "metadata_json": {"hook": "Stop scrolling", "visualStyle": "UGC", "emotionalAngle": "curiosity"},
            "avg_roas": 3.5,
            "total_conversions": 10,
        }
        result = _build_variation_prompt(winner, "iteration", {"product": "rings", "industry": "jewelry"})
        self.assertIn("iteration", result.lower())
        self.assertIn("Stop scrolling", result)
        self.assertIn("UGC", result)

    def test_opposite_prompt(self):
        from app import _build_variation_prompt
        winner = {
            "prompt_text": "original",
            "metadata_json": {"visualStyle": "UGC"},
            "avg_roas": 2.0,
            "total_conversions": 5,
        }
        result = _build_variation_prompt(winner, "opposite", None)
        self.assertIn("contrasting", result.lower())
        self.assertIn("editorial", result)

    def test_similar_prompt(self):
        from app import _build_variation_prompt
        winner = {
            "prompt_text": "product on table",
            "metadata_json": {"hook": "Don't miss this", "visualStyle": "minimal", "emotionalAngle": "urgency"},
            "avg_roas": 4.0,
            "total_conversions": 20,
        }
        result = _build_variation_prompt(winner, "similar", {"product": "candles"})
        self.assertIn("similar", result.lower())
        self.assertIn("Don't miss this", result)
        self.assertIn("minimal", result)

    def test_fresh_angle_prompt(self):
        from app import _build_variation_prompt
        winner = {
            "prompt_text": "lifestyle shot",
            "metadata_json": {"hook": "Ready for this?"},
            "avg_roas": 3.0,
            "total_conversions": 8,
        }
        result = _build_variation_prompt(winner, "fresh-angle", None)
        self.assertIn("fresh", result.lower())
        self.assertIn("Ready for this?", result)

    def test_platform_adapted_prompt(self):
        from app import _build_variation_prompt
        winner = {
            "prompt_text": "UGC style ad",
            "metadata_json": {"hook": "Watch this"},
            "platform": "tiktok",
            "avg_roas": 2.5,
            "total_conversions": 12,
        }
        result = _build_variation_prompt(winner, "platform-adapted", None)
        self.assertIn("adapt", result.lower())
        self.assertIn("tiktok", result.lower())

    def test_classification_in_prompt(self):
        from app import _build_variation_prompt
        winner = {
            "prompt_text": "test",
            "metadata_json": {},
            "avg_roas": 3.0,
            "total_conversions": 10,
            "classification": "WINNER",
            "performance_score": 0.85,
        }
        result = _build_variation_prompt(winner, "iteration", None)
        self.assertIn("WINNER", result)
        self.assertIn("0.85", result)


class TestFromWinnerResponseClassification(unittest.TestCase):
    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._query_winner_asset")
    @patch("app._is_provider_configured", return_value=False)
    def test_classification_in_response(self, mock_provider, mock_winner, mock_persist, mock_biz):
        winner_id = "550e8400-e29b-41d4-a716-446655440000"
        business_id = "660e8400-e29b-41d4-a716-446655440000"
        mock_winner.return_value = {
            "id": winner_id,
            "business_id": business_id,
            "asset_type": "image",
            "platform": "meta",
            "prompt_text": "winning prompt",
            "metadata_json": {"hook": "Stop scrolling"},
            "trend_context_json": None,
            "total_impressions": 5000,
            "total_clicks": 200,
            "total_conversions": 10,
            "avg_roas": 3.5,
            "classification": "WINNER",
            "performance_score": 0.85,
            "confidence_score": 0.92,
        }
        mock_biz.return_value = {"business_name": "TestBiz", "industry": "jewelry", "product": "rings"}
        resp = client.post("/generate/creative-assets/from-winner", json={
            "winnerAssetId": winner_id,
            "businessId": business_id,
            "variationType": "similar",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["winnerClassification"], "WINNER")
        self.assertAlmostEqual(data["winnerPerformanceScore"], 0.85, places=2)
        self.assertAlmostEqual(data["winnerConfidenceScore"], 0.92, places=2)

    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._query_winner_asset")
    @patch("app._is_provider_configured", return_value=False)
    def test_null_classification_in_response(self, mock_provider, mock_winner, mock_persist, mock_biz):
        winner_id = "550e8400-e29b-41d4-a716-446655440000"
        business_id = "660e8400-e29b-41d4-a716-446655440000"
        mock_winner.return_value = {
            "id": winner_id,
            "business_id": business_id,
            "asset_type": "image",
            "platform": "meta",
            "prompt_text": "old prompt",
            "metadata_json": None,
            "trend_context_json": None,
            "total_impressions": 100,
            "total_clicks": 2,
            "total_conversions": 0,
            "avg_roas": 0,
            "classification": None,
            "performance_score": None,
            "confidence_score": None,
        }
        mock_biz.return_value = {"business_name": "TestBiz", "industry": "tech", "product": "app"}
        resp = client.post("/generate/creative-assets/from-winner", json={
            "winnerAssetId": winner_id,
            "businessId": business_id,
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertIsNone(data["winnerClassification"])
        self.assertIsNone(data["winnerPerformanceScore"])


class TestFromRecommendationValidation(unittest.TestCase):
    def test_missing_recommendation_id(self):
        resp = client.post("/generate/creative-assets/from-recommendation", json={})
        self.assertEqual(resp.status_code, 422)

    def test_invalid_variation_mode(self):
        resp = client.post("/generate/creative-assets/from-recommendation", json={
            "recommendationId": "550e8400-e29b-41d4-a716-446655440000",
            "variationMode": "invalid-mode",
        })
        self.assertEqual(resp.status_code, 422)

    def test_count_exceeds_max(self):
        resp = client.post("/generate/creative-assets/from-recommendation", json={
            "recommendationId": "550e8400-e29b-41d4-a716-446655440000",
            "count": 10,
        })
        self.assertEqual(resp.status_code, 422)


class TestFromRecommendationEndpoint(unittest.TestCase):
    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._query_winner_asset")
    @patch("app._query_recommendation")
    @patch("app._is_provider_configured", return_value=False)
    def test_stubbed_from_recommendation(self, mock_provider, mock_rec, mock_winner, mock_persist, mock_biz):
        rec_id = "550e8400-e29b-41d4-a716-446655440000"
        business_id = "660e8400-e29b-41d4-a716-446655440000"
        asset_id = "770e8400-e29b-41d4-a716-446655440000"
        mock_rec.return_value = {
            "id": rec_id,
            "business_id": business_id,
            "creative_asset_id": asset_id,
            "recommendation_type": "SCALE",
            "priority": "HIGH",
            "title": "Scale top performer",
            "description": "Increase budget on this asset — ROAS is 3.5",
            "reasoning_json": {"classification": "WINNER"},
            "suggested_next_action": "Increase budget",
            "status": "OPEN",
            "metadata_json": None,
        }
        mock_winner.return_value = {
            "id": asset_id,
            "business_id": business_id,
            "asset_type": "image",
            "platform": "meta",
            "prompt_text": "original winning prompt",
            "metadata_json": {"hook": "Stop scrolling", "visualStyle": "UGC"},
            "trend_context_json": None,
            "total_impressions": 5000,
            "total_clicks": 200,
            "total_conversions": 10,
            "avg_roas": 3.5,
        }
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
        }
        resp = client.post("/generate/creative-assets/from-recommendation", json={
            "recommendationId": rec_id,
            "variationMode": "similar",
            "count": 2,
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "STUBBED")
        self.assertEqual(data["recommendationId"], rec_id)
        self.assertEqual(len(data["assets"]), 2)
        self.assertEqual(mock_persist.call_count, 2)

    @patch("app._query_recommendation")
    def test_recommendation_not_found(self, mock_rec):
        mock_rec.return_value = None
        resp = client.post("/generate/creative-assets/from-recommendation", json={
            "recommendationId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 404)
        self.assertIn("NOT_FOUND", resp.json()["error"])

    @patch("app._query_recommendation")
    def test_stop_recommendation_blocked(self, mock_rec):
        mock_rec.return_value = {
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "business_id": "660e8400-e29b-41d4-a716-446655440000",
            "creative_asset_id": None,
            "recommendation_type": "STOP",
            "priority": "HIGH",
            "title": "Stop underperformer",
            "description": "Pause this asset",
            "reasoning_json": {},
            "suggested_next_action": "Pause",
            "status": "OPEN",
            "metadata_json": None,
        }
        resp = client.post("/generate/creative-assets/from-recommendation", json={
            "recommendationId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 400)
        self.assertIn("STOP", resp.json()["message"])

    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._query_recommendation")
    @patch("app._is_provider_configured", return_value=False)
    def test_stop_recommendation_allowed_via_metadata(self, mock_provider, mock_rec, mock_persist, mock_biz):
        rec_id = "550e8400-e29b-41d4-a716-446655440000"
        mock_rec.return_value = {
            "id": rec_id,
            "business_id": "660e8400-e29b-41d4-a716-446655440000",
            "creative_asset_id": None,
            "recommendation_type": "STOP",
            "priority": "HIGH",
            "title": "Stop underperformer",
            "description": "Pause this asset",
            "reasoning_json": {},
            "suggested_next_action": "Pause",
            "status": "OPEN",
            "metadata_json": {"allow_generate": True},
        }
        mock_biz.return_value = {"business_name": "TestBiz", "industry": "tech", "product": "app"}
        resp = client.post("/generate/creative-assets/from-recommendation", json={
            "recommendationId": rec_id,
        })
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["status"], "STUBBED")

    @patch("app._fetch_business")
    @patch("app._persist_asset")
    @patch("app._query_winner_asset")
    @patch("app._query_recommendation")
    @patch("app._is_provider_configured", return_value=False)
    def test_adapt_for_platform_overrides_platform(self, mock_provider, mock_rec, mock_winner, mock_persist, mock_biz):
        rec_id = "550e8400-e29b-41d4-a716-446655440000"
        asset_id = "770e8400-e29b-41d4-a716-446655440000"
        mock_rec.return_value = {
            "id": rec_id,
            "business_id": "660e8400-e29b-41d4-a716-446655440000",
            "creative_asset_id": asset_id,
            "recommendation_type": "ADAPT_FOR_PLATFORM",
            "priority": "MEDIUM",
            "title": "Adapt for tiktok",
            "description": "Replicate winning approach for tiktok",
            "reasoning_json": {},
            "suggested_next_action": "Create platform-adapted version",
            "status": "OPEN",
            "metadata_json": None,
        }
        mock_winner.return_value = {
            "id": asset_id,
            "business_id": "660e8400-e29b-41d4-a716-446655440000",
            "asset_type": "image",
            "platform": "meta",
            "prompt_text": "original prompt",
            "metadata_json": {},
            "trend_context_json": None,
            "total_impressions": 5000,
            "total_clicks": 200,
            "total_conversions": 10,
            "avg_roas": 3.0,
        }
        mock_biz.return_value = {"business_name": "TestBiz", "industry": "fashion", "product": "dresses"}
        resp = client.post("/generate/creative-assets/from-recommendation", json={
            "recommendationId": rec_id,
            "variationMode": "platform-adapted",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "STUBBED")


class TestRecommendationPromptBuilder(unittest.TestCase):
    def test_scale_prompt_includes_winning_formula(self):
        from app import _build_recommendation_prompt
        rec = {"recommendation_type": "SCALE", "title": "Scale top performer", "description": "ROAS is 3.5"}
        winner = {"prompt_text": "original prompt", "metadata_json": {"hook": "Stop scrolling", "visualStyle": "UGC", "emotionalAngle": "curiosity"}, "avg_roas": 3.5, "total_conversions": 10}
        result = _build_recommendation_prompt(rec, winner, {"product": "rings", "industry": "jewelry"}, "similar")
        self.assertIn("winning formula", result.lower())
        self.assertIn("Stop scrolling", result)
        self.assertIn("UGC", result)
        self.assertIn("subtle variations", result.lower())

    def test_test_more_prompt_includes_controlled(self):
        from app import _build_recommendation_prompt
        rec = {"recommendation_type": "TEST_MORE", "title": "Test more variants", "description": "Needs more data"}
        result = _build_recommendation_prompt(rec, None, None, "fresh-angle")
        self.assertIn("controlled", result.lower())
        self.assertIn("entirely different", result.lower())

    def test_adapt_platform_prompt(self):
        from app import _build_recommendation_prompt
        rec = {"recommendation_type": "ADAPT_FOR_PLATFORM", "title": "Adapt for TikTok", "description": "TikTok adaptation"}
        result = _build_recommendation_prompt(rec, None, None, "platform-adapted")
        self.assertIn("platform", result.lower())

    def test_duplicate_winner_prompt(self):
        from app import _build_recommendation_prompt
        rec = {"recommendation_type": "DUPLICATE_WINNER", "title": "Duplicate winner", "description": "Replicate success"}
        result = _build_recommendation_prompt(rec, None, {"product": "app", "industry": "tech"}, "similar")
        self.assertIn("similar", result.lower())
        self.assertIn("tech", result.lower())

    def test_prompt_includes_performance_data(self):
        from app import _build_recommendation_prompt
        rec = {"recommendation_type": "SCALE", "title": "Scale it", "description": "Great performance"}
        winner = {"prompt_text": "base", "metadata_json": {}, "avg_roas": 4.2, "total_conversions": 25}
        result = _build_recommendation_prompt(rec, winner, None, "similar")
        self.assertIn("4.20", result)
        self.assertIn("25", result)


# ===========================================================================
# Phase 2 Tests — Landing Page Generator
# ===========================================================================

class TestLandingPageValidation(unittest.TestCase):
    def test_missing_business_id(self):
        resp = client.post("/generate/landing-page", json={})
        self.assertEqual(resp.status_code, 422)

    def test_invalid_platform(self):
        resp = client.post("/generate/landing-page", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "platform": "snapchat",
        })
        self.assertEqual(resp.status_code, 422)


class TestLandingPageEndpoint(unittest.TestCase):
    @patch("app._persist_landing_page")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=False)
    def test_stubbed_landing_page(self, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        resp = client.post("/generate/landing-page", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "platform": "meta",
            "objective": "conversions",
            "productFocus": "gold rings",
            "tone": "premium",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "STUBBED")
        self.assertEqual(data["provider"], "STUB")
        self.assertIsNotNone(data["landingPageId"])
        self.assertIsNotNone(data["metaTitle"])
        self.assertIsNotNone(data["metaDescription"])
        self.assertIsInstance(data["sections"], list)
        self.assertEqual(len(data["sections"]), 6)
        section_types = [s["sectionType"] for s in data["sections"]]
        self.assertIn("hero", section_types)
        self.assertIn("features", section_types)
        self.assertIn("social-proof", section_types)
        self.assertIn("offer", section_types)
        self.assertIn("faq", section_types)
        self.assertIn("final-cta", section_types)
        mock_persist.assert_called_once()

    @patch("app._persist_landing_page")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=True)
    @patch("app._call_openai_chat")
    def test_openai_landing_page(self, mock_chat_call, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        mock_chat_call.return_value = json.dumps({
            "metaTitle": "TestBiz Gold Rings",
            "metaDescription": "Beautiful gold rings",
            "sections": [
                {"sectionType": "hero", "headline": "AI headline", "body": "AI body", "ctaText": "Buy Now", "ctaUrl": "#buy"}
            ],
        })
        resp = client.post("/generate/landing-page", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "platform": "meta",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "SUCCESS")
        self.assertEqual(data["provider"], "OPENAI")
        self.assertEqual(data["metaTitle"], "TestBiz Gold Rings")
        self.assertEqual(len(data["sections"]), 1)
        self.assertEqual(data["sections"][0]["headline"], "AI headline")
        mock_chat_call.assert_called_once()

    @patch("app._persist_landing_page")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=True)
    @patch("app._call_openai_chat", side_effect=Exception("API error"))
    def test_landing_page_api_failure_falls_back(self, mock_chat_call, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "tech",
            "product": "app",
            "price_range": "free",
            "location": "SF",
            "target_audience": "developers",
        }
        resp = client.post("/generate/landing-page", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "FAILED")
        self.assertIsNotNone(data["sections"])
        self.assertTrue(len(data["sections"]) > 0)

    @patch("app._persist_landing_page")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=True)
    @patch("app._call_openai_chat", return_value="not valid json {{{")
    def test_landing_page_json_parse_fallback(self, mock_chat_call, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "tech",
            "product": "app",
            "price_range": "free",
            "location": "SF",
            "target_audience": "developers",
        }
        resp = client.post("/generate/landing-page", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "PARTIAL")
        self.assertEqual(data["provider"], "OPENAI_PARSE_FALLBACK")
        self.assertTrue(len(data["sections"]) > 0)

    @patch("app._fetch_business")
    def test_landing_page_business_not_found(self, mock_biz):
        mock_biz.return_value = None
        resp = client.post("/generate/landing-page", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 400)
        self.assertIn("Business not found", resp.json()["message"])

    @patch("app._persist_landing_page")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=False)
    def test_landing_page_with_strategy_context(self, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "retail",
            "product": "shoes",
            "price_range": "$100-200",
            "location": "LA",
            "target_audience": "runners",
        }
        resp = client.post("/generate/landing-page", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "platform": "google",
            "objective": "conversions",
            "landingPageRecommendations": {
                "pageTarget": "product page",
                "conversionElements": ["testimonials", "urgency timer"],
                "messagingAngle": "performance meets comfort",
            },
            "offerStrategy": {
                "promotion": "20% off first pair",
                "offer": "Free shipping over $150",
            },
            "creativesNeeded": [
                {"hook": "Run faster", "headline": "Performance Running Shoes"},
                {"hook": "Comfort guaranteed"},
            ],
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertIn("shoes", data["promptUsed"].lower())
        self.assertIn("product page", data["promptUsed"])
        self.assertIn("20% off first pair", data["promptUsed"])
        self.assertIn("Run faster", data["promptUsed"])


class TestLandingPagePromptBuilder(unittest.TestCase):
    def test_basic_prompt(self):
        req = LandingPageRequest(businessId="test-id", objective="conversions", platform="meta")
        result = LandingPagePromptBuilder.build_user_prompt(
            {"business_name": "TestBiz", "industry": "tech", "product": "app", "target_audience": "developers"},
            req,
        )
        self.assertIn("TestBiz", result)
        self.assertIn("tech", result)
        self.assertIn("conversions", result)
        self.assertIn("meta", result)

    def test_empty_prompt_fallback(self):
        req = LandingPageRequest(businessId="test-id")
        result = LandingPagePromptBuilder.build_user_prompt(None, req)
        self.assertIn("high-converting", result.lower())

    def test_prompt_with_all_strategy_fields(self):
        req = LandingPageRequest(
            businessId="test-id",
            productFocus="gold necklace",
            targetAudience="women 25-45",
            tone="luxury",
            offerHeadline="50% off today",
            landingPageRecommendations={"pageTarget": "product detail", "conversionElements": ["reviews"]},
            offerStrategy={"promotion": "half price sale"},
            creativesNeeded=[{"hook": "Shine brighter"}, {"headline": "Pure Gold"}],
        )
        result = LandingPagePromptBuilder.build_user_prompt(
            {"business_name": "GoldCo", "industry": "jewelry"},
            req,
        )
        self.assertIn("gold necklace", result)
        self.assertIn("luxury", result)
        self.assertIn("50% off today", result)
        self.assertIn("product detail", result)
        self.assertIn("half price sale", result)
        self.assertIn("Shine brighter", result)


class TestListLandingPages(unittest.TestCase):
    @patch("app._query_landing_pages")
    def test_list_landing_pages(self, mock_query):
        from datetime import datetime, timezone
        mock_query.return_value = [{
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "business_id": "660e8400-e29b-41d4-a716-446655440000",
            "strategy_request_id": None,
            "platform": "meta",
            "objective": "conversions",
            "sections_json": [{"sectionType": "hero", "headline": "Test"}],
            "full_html": None,
            "meta_title": "Test Page",
            "meta_description": "Test description",
            "prompt_used": "test prompt",
            "provider": "STUB",
            "status": "STUBBED",
            "context_json": None,
            "created_at": datetime(2026, 1, 1, tzinfo=timezone.utc),
        }]
        resp = client.get("/generate/landing-pages?businessId=660e8400-e29b-41d4-a716-446655440000")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["count"], 1)
        self.assertEqual(data["landingPages"][0]["metaTitle"], "Test Page")


# ===========================================================================
# Phase 2 Tests — Offer Generator
# ===========================================================================

class TestOfferValidation(unittest.TestCase):
    def test_missing_business_id(self):
        resp = client.post("/generate/offer", json={})
        self.assertEqual(resp.status_code, 422)

    def test_invalid_platform(self):
        resp = client.post("/generate/offer", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "platform": "snapchat",
        })
        self.assertEqual(resp.status_code, 422)

    def test_invalid_offer_type(self):
        resp = client.post("/generate/offer", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "offerType": "bogo",
        })
        self.assertEqual(resp.status_code, 422)


class TestOfferEndpoint(unittest.TestCase):
    @patch("app._persist_offer")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=False)
    def test_stubbed_offer(self, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        resp = client.post("/generate/offer", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "platform": "meta",
            "offerType": "discount",
            "productFocus": "gold rings",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "STUBBED")
        self.assertEqual(data["provider"], "STUB")
        self.assertIsNotNone(data["offerId"])
        self.assertIsNotNone(data["headline"])
        self.assertIn("Save", data["headline"])
        self.assertIsNotNone(data["description"])
        self.assertIsNotNone(data["terms"])
        self.assertIsNotNone(data["urgencyHook"])
        self.assertIsNotNone(data["ctaPrimary"])
        self.assertIsInstance(data["ctaVariants"], list)
        self.assertEqual(len(data["ctaVariants"]), 3)
        self.assertIsNotNone(data["valueProposition"])
        mock_persist.assert_called_once()

    @patch("app._persist_offer")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=True)
    @patch("app._call_openai_chat")
    def test_openai_offer(self, mock_chat_call, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        mock_chat_call.return_value = json.dumps({
            "headline": "AI-Generated Offer",
            "description": "Amazing offer from AI",
            "terms": "Limited time",
            "urgencyHook": "Ends tonight",
            "ctaPrimary": "Buy Now",
            "ctaVariants": ["Shop Now", "Get Deal"],
            "valueProposition": "Best value",
            "emailSubjectLine": "Don't miss this",
            "adCopySnippet": "Amazing deal on rings",
        })
        resp = client.post("/generate/offer", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "SUCCESS")
        self.assertEqual(data["provider"], "OPENAI")
        self.assertEqual(data["headline"], "AI-Generated Offer")
        self.assertEqual(data["emailSubjectLine"], "Don't miss this")
        mock_chat_call.assert_called_once()

    @patch("app._persist_offer")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=True)
    @patch("app._call_openai_chat", side_effect=Exception("API error"))
    def test_offer_api_failure_falls_back(self, mock_chat_call, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "tech",
            "product": "app",
            "price_range": "free",
            "location": "SF",
            "target_audience": "developers",
        }
        resp = client.post("/generate/offer", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "FAILED")
        self.assertIsNotNone(data["headline"])

    @patch("app._persist_offer")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=True)
    @patch("app._call_openai_chat", return_value="broken json {{")
    def test_offer_json_parse_fallback(self, mock_chat_call, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "tech",
            "product": "app",
            "price_range": "free",
            "location": "SF",
            "target_audience": "developers",
        }
        resp = client.post("/generate/offer", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "PARTIAL")
        self.assertEqual(data["provider"], "OPENAI_PARSE_FALLBACK")

    @patch("app._fetch_business")
    def test_offer_business_not_found(self, mock_biz):
        mock_biz.return_value = None
        resp = client.post("/generate/offer", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 400)
        self.assertIn("Business not found", resp.json()["message"])

    @patch("app._persist_offer")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=False)
    def test_offer_all_types_produce_different_stubs(self, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "retail",
            "product": "shoes",
            "price_range": "$50-100",
            "location": "LA",
            "target_audience": "runners",
        }
        headlines = set()
        for offer_type in ["discount", "free-trial", "free-shipping", "bundle", "limited-time", "lead-magnet", "consultation", "demo"]:
            resp = client.post("/generate/offer", json={
                "businessId": "550e8400-e29b-41d4-a716-446655440000",
                "offerType": offer_type,
            })
            self.assertEqual(resp.status_code, 200)
            headlines.add(resp.json()["headline"])
        # All 8 offer types produce unique headlines
        self.assertEqual(len(headlines), 8)

    @patch("app._persist_offer")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=False)
    def test_offer_with_strategy_context(self, mock_chat, mock_biz, mock_persist):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "fitness",
            "product": "protein powder",
            "price_range": "$30-60",
            "location": "US",
            "target_audience": "athletes",
        }
        resp = client.post("/generate/offer", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "offerType": "discount",
            "offerStrategy": {
                "promotion": "Summer sale 25% off",
                "offer": "Buy 2 get 1 free",
                "cta": "Stock Up Now",
            },
            "customerPersona": {
                "painPoints": ["expensive supplements", "bland taste"],
                "motivations": ["muscle recovery", "convenience"],
                "objections": ["too many options", "not sure it works"],
            },
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertIn("Summer sale 25% off", data["promptUsed"])
        self.assertIn("muscle recovery", data["promptUsed"])


class TestOfferPromptBuilder(unittest.TestCase):
    def test_basic_prompt(self):
        req = OfferRequest(businessId="test-id", offerType="discount", platform="meta")
        result = OfferPromptBuilder.build_user_prompt(
            {"business_name": "TestBiz", "industry": "retail", "product": "shoes", "target_audience": "runners", "price_range": "$50"},
            req,
        )
        self.assertIn("TestBiz", result)
        self.assertIn("discount", result)
        self.assertIn("$50", result)

    def test_empty_prompt_fallback(self):
        req = OfferRequest(businessId="test-id")
        result = OfferPromptBuilder.build_user_prompt(None, req)
        self.assertIn("promotional offer", result.lower())

    def test_prompt_includes_offer_type_guidance(self):
        req = OfferRequest(businessId="test-id", offerType="free-trial")
        result = OfferPromptBuilder.build_user_prompt(None, req)
        self.assertIn("risk-free", result.lower())


class TestListOffers(unittest.TestCase):
    @patch("app._query_offers")
    def test_list_offers(self, mock_query):
        from datetime import datetime, timezone
        mock_query.return_value = [{
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "business_id": "660e8400-e29b-41d4-a716-446655440000",
            "strategy_request_id": None,
            "platform": "meta",
            "offer_type": "discount",
            "headline": "Save 20%",
            "description": "Great deal",
            "terms": "Limited time",
            "urgency_hook": "Ends soon",
            "cta_primary": "Buy Now",
            "cta_variants": ["Shop Now"],
            "value_proposition": "Best value",
            "prompt_used": "test prompt",
            "provider": "STUB",
            "status": "STUBBED",
            "context_json": None,
            "created_at": datetime(2026, 1, 1, tzinfo=timezone.utc),
        }]
        resp = client.get("/generate/offers?businessId=660e8400-e29b-41d4-a716-446655440000")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["count"], 1)
        self.assertEqual(data["offers"][0]["headline"], "Save 20%")


# ===========================================================================
# Phase 2 Tests — Enhanced Launch Package
# ===========================================================================

class TestLaunchPackageValidation(unittest.TestCase):
    def test_missing_business_id(self):
        resp = client.post("/generate/launch-package", json={
            "recommendationId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 422)

    def test_missing_recommendation_id(self):
        resp = client.post("/generate/launch-package", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 422)

    def test_invalid_platform(self):
        resp = client.post("/generate/launch-package", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "recommendationId": "660e8400-e29b-41d4-a716-446655440000",
            "platform": "snapchat",
        })
        self.assertEqual(resp.status_code, 422)


class TestLaunchPackageEndpoint(unittest.TestCase):
    @patch("app._persist_launch_package")
    @patch("app._persist_offer")
    @patch("app._persist_landing_page")
    @patch("app._query_recommendation")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=False)
    def test_stubbed_launch_package(self, mock_chat, mock_biz, mock_rec, mock_lp, mock_offer, mock_pkg):
        business_id = "660e8400-e29b-41d4-a716-446655440000"
        rec_id = "550e8400-e29b-41d4-a716-446655440000"
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        mock_rec.return_value = {
            "id": rec_id,
            "business_id": business_id,
            "creative_asset_id": None,
            "recommendation_type": "SCALE",
            "priority": "HIGH",
            "title": "Scale top performer",
            "description": "ROAS is 3.5 — increase budget",
            "reasoning_json": {},
            "suggested_next_action": "Increase budget",
            "status": "OPEN",
            "metadata_json": None,
        }
        resp = client.post("/generate/launch-package", json={
            "businessId": business_id,
            "recommendationId": rec_id,
            "platform": "meta",
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "STUBBED")
        self.assertIsNotNone(data["packageId"])
        self.assertEqual(data["platform"], "meta")

        # Verify landing page
        lp = data["landingPage"]
        self.assertIsNotNone(lp["landingPageId"])
        self.assertEqual(lp["status"], "STUBBED")
        self.assertIsInstance(lp["sections"], list)
        self.assertTrue(len(lp["sections"]) > 0)

        # Verify offer
        offer = data["offer"]
        self.assertIsNotNone(offer["offerId"])
        self.assertEqual(offer["status"], "STUBBED")
        self.assertIsNotNone(offer["headline"])
        self.assertIsNotNone(offer["ctaPrimary"])

        # Verify campaign strategy
        strategy = data["campaignStrategy"]
        self.assertIsNotNone(strategy["campaignBrief"])
        self.assertIsNotNone(strategy["budgetAllocation"])
        self.assertIsNotNone(strategy["kpiTargets"])
        self.assertIsInstance(strategy["launchTimeline"], list)
        self.assertIsInstance(strategy["optimizationPlaybook"], list)

        # Verify recommendation info
        self.assertEqual(data["recommendation"]["recommendationId"], rec_id)
        self.assertEqual(data["recommendation"]["type"], "SCALE")

        mock_lp.assert_called_once()
        mock_offer.assert_called_once()
        mock_pkg.assert_called_once()

    @patch("app._persist_launch_package")
    @patch("app._persist_offer")
    @patch("app._persist_landing_page")
    @patch("app._query_recommendation")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=True)
    @patch("app._call_openai_chat")
    def test_openai_launch_package(self, mock_chat_call, mock_chat, mock_biz, mock_rec, mock_lp, mock_offer, mock_pkg):
        business_id = "660e8400-e29b-41d4-a716-446655440000"
        rec_id = "550e8400-e29b-41d4-a716-446655440000"
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "jewelry",
            "product": "rings",
            "price_range": "mid",
            "location": "NYC",
            "target_audience": "women 25-45",
        }
        mock_rec.return_value = {
            "id": rec_id,
            "business_id": business_id,
            "creative_asset_id": None,
            "recommendation_type": "SCALE",
            "priority": "HIGH",
            "title": "Scale",
            "description": "Scale it",
            "reasoning_json": {},
            "suggested_next_action": "Scale",
            "status": "OPEN",
            "metadata_json": None,
        }
        # Return different responses for each of the 3 calls
        mock_chat_call.side_effect = [
            # Landing page
            json.dumps({
                "metaTitle": "AI Page",
                "metaDescription": "AI Description",
                "sections": [{"sectionType": "hero", "headline": "AI Hero", "body": "AI Body", "ctaText": "Buy", "ctaUrl": "#"}],
            }),
            # Offer
            json.dumps({
                "headline": "AI Offer",
                "description": "AI offer desc",
                "terms": "None",
                "urgencyHook": "Now",
                "ctaPrimary": "Buy Now",
                "ctaVariants": ["Get It"],
                "valueProposition": "Best",
                "emailSubjectLine": "Subject",
                "adCopySnippet": "Ad copy",
            }),
            # Campaign strategy
            json.dumps({
                "campaignBrief": "AI brief",
                "audienceStrategy": "AI audience",
                "budgetAllocation": {"dailyBudget": "$50", "testingPhase": "3 days", "scalingTrigger": "ROAS > 2"},
                "creativeRotation": [{"slot": "Primary", "description": "Main", "priority": "HIGH"}],
                "launchTimeline": [{"day": "Day 0", "action": "Launch"}],
                "kpiTargets": {"primaryKpi": "ROAS", "target": "> 2.0", "secondaryKpis": ["CTR > 1.5%"]},
                "optimizationPlaybook": [{"trigger": "Low CTR", "action": "Change creative"}],
            }),
        ]
        resp = client.post("/generate/launch-package", json={
            "businessId": business_id,
            "recommendationId": rec_id,
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "SUCCESS")
        self.assertEqual(data["landingPage"]["metaTitle"], "AI Page")
        self.assertEqual(data["offer"]["headline"], "AI Offer")
        self.assertEqual(data["campaignStrategy"]["campaignBrief"], "AI brief")
        self.assertEqual(mock_chat_call.call_count, 3)

    @patch("app._fetch_business")
    def test_launch_package_business_not_found(self, mock_biz):
        mock_biz.return_value = None
        resp = client.post("/generate/launch-package", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "recommendationId": "660e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 400)
        self.assertIn("Business not found", resp.json()["message"])

    @patch("app._query_recommendation")
    @patch("app._fetch_business")
    def test_launch_package_recommendation_not_found(self, mock_biz, mock_rec):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "tech",
            "product": "app",
        }
        mock_rec.return_value = None
        resp = client.post("/generate/launch-package", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "recommendationId": "660e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 404)
        self.assertIn("Recommendation not found", resp.json()["message"])

    @patch("app._query_recommendation")
    @patch("app._fetch_business")
    def test_launch_package_business_mismatch(self, mock_biz, mock_rec):
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "tech",
            "product": "app",
        }
        mock_rec.return_value = {
            "id": "660e8400-e29b-41d4-a716-446655440000",
            "business_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "creative_asset_id": None,
            "recommendation_type": "SCALE",
            "priority": "HIGH",
            "title": "Scale",
            "description": "Scale it",
            "reasoning_json": {},
            "suggested_next_action": "Scale",
            "status": "OPEN",
            "metadata_json": None,
        }
        resp = client.post("/generate/launch-package", json={
            "businessId": "550e8400-e29b-41d4-a716-446655440000",
            "recommendationId": "660e8400-e29b-41d4-a716-446655440000",
        })
        self.assertEqual(resp.status_code, 400)
        self.assertIn("does not belong", resp.json()["message"])

    @patch("app._persist_launch_package")
    @patch("app._persist_offer")
    @patch("app._persist_landing_page")
    @patch("app._query_recommendation")
    @patch("app._fetch_business")
    @patch("app._is_chat_configured", return_value=False)
    def test_launch_package_platform_detection_from_title(self, mock_chat, mock_biz, mock_rec, mock_lp, mock_offer, mock_pkg):
        business_id = "660e8400-e29b-41d4-a716-446655440000"
        rec_id = "550e8400-e29b-41d4-a716-446655440000"
        mock_biz.return_value = {
            "business_name": "TestBiz",
            "industry": "fashion",
            "product": "dresses",
        }
        mock_rec.return_value = {
            "id": rec_id,
            "business_id": business_id,
            "creative_asset_id": None,
            "recommendation_type": "ADAPT_FOR_PLATFORM",
            "priority": "MEDIUM",
            "title": "Adapt for tiktok",
            "description": "Create tiktok version",
            "reasoning_json": {},
            "suggested_next_action": "Create",
            "status": "OPEN",
            "metadata_json": None,
        }
        resp = client.post("/generate/launch-package", json={
            "businessId": business_id,
            "recommendationId": rec_id,
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["platform"], "tiktok")


class TestLaunchPackagePromptBuilder(unittest.TestCase):
    def test_basic_prompt(self):
        req = EnhancedLaunchPackageRequest(
            businessId="test-id",
            recommendationId="rec-id",
            platform="meta",
        )
        rec = {"recommendation_type": "SCALE", "title": "Scale it", "description": "Great ROAS", "priority": "HIGH"}
        result = LaunchPackagePromptBuilder.build_user_prompt(
            {"business_name": "TestBiz", "industry": "tech", "product": "app", "target_audience": "developers"},
            rec,
            req,
        )
        self.assertIn("TestBiz", result)
        self.assertIn("SCALE", result)
        self.assertIn("Scale it", result)
        self.assertIn("meta", result)

    def test_empty_prompt_fallback(self):
        req = EnhancedLaunchPackageRequest(businessId="test-id", recommendationId="rec-id")
        result = LaunchPackagePromptBuilder.build_user_prompt(None, None, req)
        self.assertIn("launch package", result.lower())


class TestListLaunchPackages(unittest.TestCase):
    @patch("app._query_launch_packages")
    def test_list_launch_packages(self, mock_query):
        from datetime import datetime, timezone
        mock_query.return_value = [{
            "id": "550e8400-e29b-41d4-a716-446655440000",
            "business_id": "660e8400-e29b-41d4-a716-446655440000",
            "recommendation_id": "770e8400-e29b-41d4-a716-446655440000",
            "strategy_request_id": None,
            "platform": "meta",
            "landing_page_id": "880e8400-e29b-41d4-a716-446655440000",
            "offer_id": "990e8400-e29b-41d4-a716-446655440000",
            "package_json": {"test": "data"},
            "prompt_used": "test prompt",
            "provider": "STUB",
            "status": "STUBBED",
            "created_at": datetime(2026, 1, 1, tzinfo=timezone.utc),
        }]
        resp = client.get("/generate/launch-packages?businessId=660e8400-e29b-41d4-a716-446655440000")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["count"], 1)
        self.assertEqual(data["launchPackages"][0]["platform"], "meta")
        self.assertIsNotNone(data["launchPackages"][0]["landingPageId"])
        self.assertIsNotNone(data["launchPackages"][0]["offerId"])


# ===========================================================================
# Phase 2 Tests — OpenAI Chat Helper
# ===========================================================================

class TestOpenAIChatHelper(unittest.TestCase):
    @patch("app.OPENAI_CHAT_API_KEY", "test-key")
    def test_is_chat_configured_true(self):
        from app import _is_chat_configured
        self.assertTrue(_is_chat_configured())

    @patch("app.OPENAI_CHAT_API_KEY", "")
    def test_is_chat_configured_false(self):
        from app import _is_chat_configured
        self.assertFalse(_is_chat_configured())

    @patch("httpx.Client")
    def test_call_openai_chat_success(self, mock_client_cls):
        from app import _call_openai_chat
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "choices": [{"message": {"content": "Hello from AI"}}]
        }
        mock_response.raise_for_status = MagicMock()
        mock_client = MagicMock()
        mock_client.__enter__ = MagicMock(return_value=mock_client)
        mock_client.__exit__ = MagicMock(return_value=False)
        mock_client.post.return_value = mock_response
        mock_client_cls.return_value = mock_client

        result = _call_openai_chat("system prompt", "user prompt", 0.7)
        self.assertEqual(result, "Hello from AI")
        mock_client.post.assert_called_once()

    @patch("httpx.Client")
    def test_call_openai_chat_no_choices_raises(self, mock_client_cls):
        from app import _call_openai_chat
        mock_response = MagicMock()
        mock_response.json.return_value = {"choices": []}
        mock_response.raise_for_status = MagicMock()
        mock_client = MagicMock()
        mock_client.__enter__ = MagicMock(return_value=mock_client)
        mock_client.__exit__ = MagicMock(return_value=False)
        mock_client.post.return_value = mock_response
        mock_client_cls.return_value = mock_client

        with self.assertRaises(RuntimeError):
            _call_openai_chat("system", "user")


if __name__ == "__main__":
    unittest.main()
