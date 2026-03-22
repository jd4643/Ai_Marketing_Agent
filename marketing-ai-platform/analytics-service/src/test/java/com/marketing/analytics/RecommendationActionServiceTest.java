package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.marketing.analytics.model.CreativeOptimizationRecommendation;
import com.marketing.analytics.repo.CreativeOptimizationRecommendationRepository;
import com.marketing.analytics.service.CreativeOptimizationRecommendationService;
import com.marketing.analytics.service.OutcomeTrackingService;
import com.marketing.analytics.service.RecommendationActionService;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationActionServiceTest {

    @Mock CreativeOptimizationRecommendationRepository recRepo;
    @Mock CreativeOptimizationRecommendationService recService;
    @Mock OutcomeTrackingService outcomeTrackingService;
    @InjectMocks RecommendationActionService service;

    private CreativeOptimizationRecommendation buildRec(String type, String priority, String status) {
        CreativeOptimizationRecommendation rec = new CreativeOptimizationRecommendation();
        rec.setId(UUID.randomUUID());
        rec.setBusinessId(UUID.randomUUID());
        rec.setCreativeAssetId(UUID.randomUUID());
        rec.setRecommendationType(type);
        rec.setPriority(priority);
        rec.setTitle("Test " + type);
        rec.setDescription("Description for " + type);
        rec.setReasoningJson("{\"volumeSufficient\":true}");
        rec.setSuggestedNextAction("Increase budget");
        rec.setStatus(status);
        rec.setCreatedAt(Instant.now());
        rec.setUpdatedAt(Instant.now());
        return rec;
    }

    @Nested class ApplyTests {

        @Test void applyOpenRecommendation() {
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));
            when(recRepo.save(any())).thenReturn(rec);
            when(recService.entityToMap(any())).thenReturn(Map.of("status", "APPLIED"));

            Map<String, Object> result = service.apply(rec.getId());

            assertEquals("APPLIED", rec.getStatus());
            assertNotNull(rec.getAppliedAt());
            verify(recRepo).save(rec);
            assertEquals("APPLIED", result.get("status"));
        }

        @Test void applyIdempotent() {
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "APPLIED");
            rec.setAppliedAt(Instant.now());
            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));
            when(recService.entityToMap(rec)).thenReturn(Map.of("status", "APPLIED"));

            Map<String, Object> result = service.apply(rec.getId());

            verify(recRepo, never()).save(any());
            assertEquals("APPLIED", result.get("status"));
        }

        @Test void applyDismissedThrows() {
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "DISMISSED");
            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            assertThrows(IllegalArgumentException.class, () -> service.apply(rec.getId()));
        }

        @Test void applyNotFoundThrows() {
            UUID id = UUID.randomUUID();
            when(recRepo.findById(id)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> service.apply(id));
        }
    }

    @Nested class DismissTests {

        @Test void dismissOpenRecommendation() {
            CreativeOptimizationRecommendation rec = buildRec("STOP", "HIGH", "OPEN");
            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));
            when(recRepo.save(any())).thenReturn(rec);
            when(recService.entityToMap(any())).thenReturn(Map.of("status", "DISMISSED"));

            Map<String, Object> result = service.dismiss(rec.getId());

            assertEquals("DISMISSED", rec.getStatus());
            assertNotNull(rec.getDismissedAt());
            verify(recRepo).save(rec);
            assertEquals("DISMISSED", result.get("status"));
        }

        @Test void dismissIdempotent() {
            CreativeOptimizationRecommendation rec = buildRec("STOP", "HIGH", "DISMISSED");
            rec.setDismissedAt(Instant.now());
            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));
            when(recService.entityToMap(rec)).thenReturn(Map.of("status", "DISMISSED"));

            Map<String, Object> result = service.dismiss(rec.getId());

            verify(recRepo, never()).save(any());
            assertEquals("DISMISSED", result.get("status"));
        }

        @Test void dismissAppliedThrows() {
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "APPLIED");
            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            assertThrows(IllegalArgumentException.class, () -> service.dismiss(rec.getId()));
        }

        @Test void dismissNotFoundThrows() {
            UUID id = UUID.randomUUID();
            when(recRepo.findById(id)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> service.dismiss(id));
        }
    }

    @Nested class DetailTests {

        @Test void getDetailOk() {
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));
            when(recService.entityToMap(rec)).thenReturn(Map.of("title", "Test SCALE"));

            Map<String, Object> result = service.getDetail(rec.getId());

            assertEquals("Test SCALE", result.get("title"));
        }

        @Test void getDetailNotFoundThrows() {
            UUID id = UUID.randomUUID();
            when(recRepo.findById(id)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> service.getDetail(id));
        }
    }

    @Nested class DashboardTests {

        @Test void dashboardGroupsByPriority() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation high = buildRec("SCALE", "HIGH", "OPEN");
            high.setBusinessId(businessId);
            CreativeOptimizationRecommendation medium = buildRec("TEST_MORE", "MEDIUM", "OPEN");
            medium.setBusinessId(businessId);
            CreativeOptimizationRecommendation low = buildRec("DUPLICATE_WINNER", "LOW", "OPEN");
            low.setBusinessId(businessId);

            when(recRepo.findByBusinessIdOrderByCreatedAtDesc(businessId))
                    .thenReturn(List.of(high, medium, low));
            when(recService.availableActions(any())).thenReturn(List.of("APPLY", "DISMISS"));

            Map<String, Object> result = service.dashboard(businessId);

            assertEquals(3, result.get("totalRecommendations"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> highCards = (List<Map<String, Object>>) result.get("highPriority");
            assertEquals(1, highCards.size());
            assertEquals("SCALE", highCards.get(0).get("type"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> medCards = (List<Map<String, Object>>) result.get("mediumPriority");
            assertEquals(1, medCards.size());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lowCards = (List<Map<String, Object>>) result.get("lowPriority");
            assertEquals(1, lowCards.size());
        }

        @Test void dashboardAllItemsHaveAvailableActions() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            rec.setBusinessId(businessId);

            when(recRepo.findByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of(rec));
            when(recService.availableActions(rec)).thenReturn(List.of("APPLY", "DISMISS", "GENERATE_VARIANTS", "EXPORT_PACKAGE"));

            Map<String, Object> result = service.dashboard(businessId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> highCards = (List<Map<String, Object>>) result.get("highPriority");
            assertNotNull(highCards.get(0).get("availableActions"));
        }

        @Test void dashboardEmpty() {
            UUID businessId = UUID.randomUUID();
            when(recRepo.findByBusinessIdOrderByCreatedAtDesc(businessId)).thenReturn(List.of());

            Map<String, Object> result = service.dashboard(businessId);
            assertEquals(0, result.get("totalRecommendations"));
        }
    }

    @Nested class ExportLaunchPackageTests {

        @Test void exportContainsAllSections() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            rec.setBusinessId(businessId);
            rec.setReasoningJson("{\"platform\":\"meta\"}");

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            Map<String, Object> pkg = service.exportLaunchPackage(businessId, rec.getId());

            assertNotNull(pkg.get("campaignName"));
            assertNotNull(pkg.get("platform"));
            assertNotNull(pkg.get("objective"));
            assertNotNull(pkg.get("budgetGuidance"));
            assertNotNull(pkg.get("targetingGuidance"));
            assertNotNull(pkg.get("copy"));
            assertNotNull(pkg.get("assetLinks"));
            assertNotNull(pkg.get("landingPageGuidance"));
            assertNotNull(pkg.get("trackingChecklist"));
            assertNotNull(pkg.get("notes"));
            assertNotNull(pkg.get("generationServiceLinks"));
        }

        @Test void exportContainsGenerationServiceLinks() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            rec.setBusinessId(businessId);
            rec.setReasoningJson("{\"platform\":\"meta\"}");

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            Map<String, Object> pkg = service.exportLaunchPackage(businessId, rec.getId());

            @SuppressWarnings("unchecked")
            Map<String, Object> links = (Map<String, Object>) pkg.get("generationServiceLinks");
            assertNotNull(links);
            assertTrue(links.containsKey("generateLandingPage"));
            assertTrue(links.containsKey("generateOffer"));
            assertTrue(links.containsKey("generateEnhancedLaunchPackage"));

            @SuppressWarnings("unchecked")
            Map<String, Object> lpLink = (Map<String, Object>) links.get("generateLandingPage");
            assertEquals("POST /generate/landing-page", lpLink.get("endpoint"));
            @SuppressWarnings("unchecked")
            Map<String, Object> lpPayload = (Map<String, Object>) lpLink.get("suggestedPayload");
            assertEquals(businessId.toString(), lpPayload.get("business_id"));
            assertEquals("meta", lpPayload.get("platform"));
            assertEquals("conversions", lpPayload.get("objective"));
        }

        @Test void exportScaleObjectiveIsConversions() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            rec.setBusinessId(businessId);

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            Map<String, Object> pkg = service.exportLaunchPackage(businessId, rec.getId());
            assertEquals("conversions", pkg.get("objective"));
        }

        @Test void exportStopObjectiveIsPause() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("STOP", "HIGH", "OPEN");
            rec.setBusinessId(businessId);

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            Map<String, Object> pkg = service.exportLaunchPackage(businessId, rec.getId());
            assertEquals("pause_and_reallocate", pkg.get("objective"));
        }

        @Test void exportWrongBusinessThrows() {
            UUID businessId = UUID.randomUUID();
            UUID otherBusinessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            rec.setBusinessId(otherBusinessId);

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            assertThrows(IllegalArgumentException.class,
                    () -> service.exportLaunchPackage(businessId, rec.getId()));
        }

        @Test void exportNotFoundThrows() {
            UUID id = UUID.randomUUID();
            when(recRepo.findById(id)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class,
                    () -> service.exportLaunchPackage(UUID.randomUUID(), id));
        }

        @Test void exportIncludesAssetLinkWhenAssetLinked() {
            UUID businessId = UUID.randomUUID();
            UUID assetId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            rec.setBusinessId(businessId);
            rec.setCreativeAssetId(assetId);

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            Map<String, Object> pkg = service.exportLaunchPackage(businessId, rec.getId());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) pkg.get("assetLinks");
            assertEquals(1, links.size());
            assertEquals(assetId, links.get(0).get("assetId"));
        }

        @Test void exportNoAssetLinkWhenNoAsset() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("STOP", "HIGH", "OPEN");
            rec.setBusinessId(businessId);
            rec.setCreativeAssetId(null);

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            Map<String, Object> pkg = service.exportLaunchPackage(businessId, rec.getId());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) pkg.get("assetLinks");
            assertTrue(links.isEmpty());
        }

        @Test void exportPlatformFromReasoningJson() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("SCALE", "HIGH", "OPEN");
            rec.setBusinessId(businessId);
            rec.setReasoningJson("{\"platform\":\"google\"}");

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            Map<String, Object> pkg = service.exportLaunchPackage(businessId, rec.getId());
            assertEquals("google", pkg.get("platform"));
        }

        @Test void exportPlatformFromTitle() {
            UUID businessId = UUID.randomUUID();
            CreativeOptimizationRecommendation rec = buildRec("ADAPT_FOR_PLATFORM", "MEDIUM", "OPEN");
            rec.setBusinessId(businessId);
            rec.setTitle("Adapt for TikTok");
            rec.setReasoningJson("{}");

            when(recRepo.findById(rec.getId())).thenReturn(Optional.of(rec));

            Map<String, Object> pkg = service.exportLaunchPackage(businessId, rec.getId());
            assertEquals("tiktok", pkg.get("platform"));
        }
    }
}
