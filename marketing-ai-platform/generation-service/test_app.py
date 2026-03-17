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


if __name__ == "__main__":
    unittest.main()
