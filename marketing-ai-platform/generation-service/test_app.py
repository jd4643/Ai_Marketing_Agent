import json
import unittest
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient

from app import app, CreativeAssetPromptBuilder, TrendContext, AssetMetadata


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


if __name__ == "__main__":
    unittest.main()
