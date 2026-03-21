package com.marketing.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.marketing.analytics.model.PerformanceSnapshot;
import com.marketing.analytics.repo.CampaignMetricRepository;
import com.marketing.analytics.repo.CreativeAssetPerformanceRepository;
import com.marketing.analytics.repo.PerformanceSnapshotRepository;
import com.marketing.analytics.service.SnapshotService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

    @Mock PerformanceSnapshotRepository snapshotRepo;
    @Mock CampaignMetricRepository metricRepo;
    @Mock CreativeAssetPerformanceRepository perfRepo;
    @InjectMocks SnapshotService service;

    static final UUID BIZ = UUID.randomUUID();
    static final UUID PLAN_ID = UUID.randomUUID();

    // ─── Capture ────────────────────────────────────────────────────────

    @Nested class CaptureTests {

        @Test void captureManualSnapshot() {
            List<Object[]> metricRows = new ArrayList<>();
            metricRows.add(new Object[]{bd(500), bd(2500), 25000L, 1000L, 50L});
            when(metricRepo.aggregateByBusinessId(eq(BIZ), any(Instant.class)))
                    .thenReturn(metricRows);
            List<Object[]> classRows = new ArrayList<>();
            classRows.add(new Object[]{"WINNER", 3L});
            classRows.add(new Object[]{"WEAK", 2L});
            when(perfRepo.countByClassification(BIZ))
                    .thenReturn(classRows);
            when(snapshotRepo.save(any(PerformanceSnapshot.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> req = Map.of("snapshotType", "MANUAL", "label", "Weekly Check");
            Map<String, Object> result = service.captureSnapshot(BIZ, req);

            assertThat(result.get("businessId")).isEqualTo(BIZ);
            assertThat(result.get("snapshotType")).isEqualTo("MANUAL");
            assertThat(result.get("label")).isEqualTo("Weekly Check");
            assertThat(result.get("metrics")).isNotNull();

            ArgumentCaptor<PerformanceSnapshot> cap = ArgumentCaptor.forClass(PerformanceSnapshot.class);
            verify(snapshotRepo).save(cap.capture());
            assertThat(cap.getValue().getBusinessId()).isEqualTo(BIZ);
            assertThat(cap.getValue().getSnapshotType()).isEqualTo("MANUAL");
        }

        @Test void capturePrePlanSnapshot() {
            List<Object[]> metricRows = new ArrayList<>();
            metricRows.add(new Object[]{bd(0), bd(0), 0L, 0L, 0L});
            when(metricRepo.aggregateByBusinessId(eq(BIZ), any(Instant.class)))
                    .thenReturn(metricRows);
            when(perfRepo.countByClassification(BIZ)).thenReturn(List.of());
            when(snapshotRepo.save(any(PerformanceSnapshot.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> req = Map.of("snapshotType", "PRE_PLAN", "planId", PLAN_ID.toString());
            Map<String, Object> result = service.captureSnapshot(BIZ, req);

            assertThat(result.get("snapshotType")).isEqualTo("PRE_PLAN");
            assertThat(result.get("planId")).isEqualTo(PLAN_ID);
        }

        @Test void captureDefaultsToManualType() {
            when(metricRepo.aggregateByBusinessId(eq(BIZ), any(Instant.class)))
                    .thenReturn(List.of());
            when(perfRepo.countByClassification(BIZ)).thenReturn(List.of());
            when(snapshotRepo.save(any(PerformanceSnapshot.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> req = Map.of();
            Map<String, Object> result = service.captureSnapshot(BIZ, req);

            assertThat(result.get("snapshotType")).isEqualTo("MANUAL");
        }

        @Test void captureHandlesNullMetricGracefully() {
            when(metricRepo.aggregateByBusinessId(eq(BIZ), any(Instant.class)))
                    .thenReturn(null);
            when(perfRepo.countByClassification(BIZ)).thenReturn(null);
            when(snapshotRepo.save(any(PerformanceSnapshot.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.captureSnapshot(BIZ, Map.of());

            assertThat(result).containsKey("metrics");
            verify(snapshotRepo).save(any());
        }

        @Test void captureHandlesMetricRepositoryException() {
            when(metricRepo.aggregateByBusinessId(eq(BIZ), any(Instant.class)))
                    .thenThrow(new RuntimeException("DB error"));
            when(perfRepo.countByClassification(BIZ)).thenReturn(List.of());
            when(snapshotRepo.save(any(PerformanceSnapshot.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = service.captureSnapshot(BIZ, Map.of());

            // Should still succeed with zero metrics
            assertThat(result).containsKey("id");
            verify(snapshotRepo).save(any());
        }
    }

    // ─── List ───────────────────────────────────────────────────────────

    @Nested class ListTests {

        @Test void listAllSnapshots() {
            PerformanceSnapshot s = makeSnapshot("MANUAL", "Test");
            when(snapshotRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ))
                    .thenReturn(List.of(s));

            List<Map<String, Object>> result = service.listSnapshots(BIZ, null, 0);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("snapshotType")).isEqualTo("MANUAL");
        }

        @Test void listByType() {
            PerformanceSnapshot s = makeSnapshot("PRE_PLAN", "Before launch");
            when(snapshotRepo.findByBusinessIdAndSnapshotTypeOrderByCreatedAtDesc(BIZ, "PRE_PLAN"))
                    .thenReturn(List.of(s));

            List<Map<String, Object>> result = service.listSnapshots(BIZ, "PRE_PLAN", 0);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("snapshotType")).isEqualTo("PRE_PLAN");
        }

        @Test void listByDays() {
            PerformanceSnapshot s = makeSnapshot("MANUAL", "Recent");
            when(snapshotRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(BIZ), any(Instant.class)))
                    .thenReturn(List.of(s));

            List<Map<String, Object>> result = service.listSnapshots(BIZ, null, 7);

            assertThat(result).hasSize(1);
        }

        @Test void listByTypeHasPriorityOverDays() {
            when(snapshotRepo.findByBusinessIdAndSnapshotTypeOrderByCreatedAtDesc(BIZ, "MANUAL"))
                    .thenReturn(List.of());

            List<Map<String, Object>> result = service.listSnapshots(BIZ, "MANUAL", 7);

            // When type is specified, it uses the type filter, not the days filter
            verify(snapshotRepo).findByBusinessIdAndSnapshotTypeOrderByCreatedAtDesc(BIZ, "MANUAL");
            verify(snapshotRepo, never()).findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(any(), any());
        }

        @Test void listPlanSnapshots() {
            PerformanceSnapshot s = makeSnapshot("PRE_PLAN", "Plan snap");
            s.setPlanId(PLAN_ID);
            when(snapshotRepo.findByPlanIdOrderByCreatedAtDesc(PLAN_ID))
                    .thenReturn(List.of(s));

            List<Map<String, Object>> result = service.listPlanSnapshots(PLAN_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).get("planId")).isEqualTo(PLAN_ID);
        }

        @Test void listEmptyResult() {
            when(snapshotRepo.findByBusinessIdOrderByCreatedAtDesc(BIZ))
                    .thenReturn(List.of());

            List<Map<String, Object>> result = service.listSnapshots(BIZ, null, 0);

            assertThat(result).isEmpty();
        }
    }

    // ─── Get ────────────────────────────────────────────────────────────

    @Nested class GetTests {

        @Test void getSnapshotOk() {
            UUID snapId = UUID.randomUUID();
            PerformanceSnapshot s = makeSnapshot("MANUAL", "Test");
            s.setId(snapId);
            when(snapshotRepo.findById(snapId)).thenReturn(Optional.of(s));

            Map<String, Object> result = service.getSnapshot(snapId);

            assertThat(result.get("id")).isEqualTo(snapId);
        }

        @Test void getSnapshotNotFound() {
            UUID snapId = UUID.randomUUID();
            when(snapshotRepo.findById(snapId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getSnapshot(snapId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Snapshot not found");
        }
    }

    // ─── Compare ────────────────────────────────────────────────────────

    @Nested class CompareTests {

        @Test void compareSnapshotsComputesDeltas() {
            UUID baseId = UUID.randomUUID();
            UUID currId = UUID.randomUUID();

            PerformanceSnapshot base = makeSnapshotWithMetrics(baseId,
                    "{\"totalSpend\":500,\"totalRevenue\":2000,\"totalImpressions\":10000,\"totalClicks\":500,\"totalConversions\":25,\"overallRoas\":4.0,\"ctr\":0.05,\"totalAssets\":10}");
            PerformanceSnapshot curr = makeSnapshotWithMetrics(currId,
                    "{\"totalSpend\":600,\"totalRevenue\":3000,\"totalImpressions\":15000,\"totalClicks\":750,\"totalConversions\":40,\"overallRoas\":5.0,\"ctr\":0.05,\"totalAssets\":12}");

            when(snapshotRepo.findById(baseId)).thenReturn(Optional.of(base));
            when(snapshotRepo.findById(currId)).thenReturn(Optional.of(curr));

            Map<String, Object> result = service.compareSnapshots(baseId, currId);

            assertThat(result.get("baselineId")).isEqualTo(baseId);
            assertThat(result.get("currentId")).isEqualTo(currId);
            assertThat(result).containsKey("deltas");

            @SuppressWarnings("unchecked")
            Map<String, Object> deltas = (Map<String, Object>) result.get("deltas");
            assertThat(deltas).containsKey("totalSpend");
            assertThat(deltas).containsKey("totalRevenue");
        }

        @Test void compareBaselineNotFound() {
            UUID baseId = UUID.randomUUID();
            UUID currId = UUID.randomUUID();
            when(snapshotRepo.findById(baseId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.compareSnapshots(baseId, currId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Baseline snapshot not found");
        }

        @Test void compareCurrentNotFound() {
            UUID baseId = UUID.randomUUID();
            UUID currId = UUID.randomUUID();
            PerformanceSnapshot base = makeSnapshot("MANUAL", "base");
            base.setId(baseId);
            when(snapshotRepo.findById(baseId)).thenReturn(Optional.of(base));
            when(snapshotRepo.findById(currId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.compareSnapshots(baseId, currId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Current snapshot not found");
        }

        @Test void compareWithZeroBaselineMetrics() {
            UUID baseId = UUID.randomUUID();
            UUID currId = UUID.randomUUID();

            PerformanceSnapshot base = makeSnapshotWithMetrics(baseId,
                    "{\"totalSpend\":0,\"totalRevenue\":0,\"totalImpressions\":0,\"totalClicks\":0,\"totalConversions\":0,\"overallRoas\":0,\"ctr\":0,\"totalAssets\":0}");
            PerformanceSnapshot curr = makeSnapshotWithMetrics(currId,
                    "{\"totalSpend\":100,\"totalRevenue\":500,\"totalImpressions\":5000,\"totalClicks\":250,\"totalConversions\":10,\"overallRoas\":5.0,\"ctr\":0.05,\"totalAssets\":5}");

            when(snapshotRepo.findById(baseId)).thenReturn(Optional.of(base));
            when(snapshotRepo.findById(currId)).thenReturn(Optional.of(curr));

            Map<String, Object> result = service.compareSnapshots(baseId, currId);

            @SuppressWarnings("unchecked")
            Map<String, Object> deltas = (Map<String, Object>) result.get("deltas");
            // When baseline is zero and current is positive, percentChange should be 100
            @SuppressWarnings("unchecked")
            Map<String, Object> spendDelta = (Map<String, Object>) deltas.get("totalSpend");
            assertThat(spendDelta.get("percentChange")).isEqualTo(new BigDecimal("100"));
        }

        @Test void compareWithNullMetricsJson() {
            UUID baseId = UUID.randomUUID();
            UUID currId = UUID.randomUUID();

            PerformanceSnapshot base = makeSnapshotWithMetrics(baseId, null);
            PerformanceSnapshot curr = makeSnapshotWithMetrics(currId, null);

            when(snapshotRepo.findById(baseId)).thenReturn(Optional.of(base));
            when(snapshotRepo.findById(currId)).thenReturn(Optional.of(curr));

            Map<String, Object> result = service.compareSnapshots(baseId, currId);

            assertThat(result).containsKey("deltas");
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private PerformanceSnapshot makeSnapshot(String type, String label) {
        PerformanceSnapshot s = new PerformanceSnapshot();
        s.setId(UUID.randomUUID());
        s.setBusinessId(BIZ);
        s.setSnapshotType(type);
        s.setLabel(label);
        s.setCreatedAt(Instant.now());
        return s;
    }

    private PerformanceSnapshot makeSnapshotWithMetrics(UUID id, String metricsJson) {
        PerformanceSnapshot s = new PerformanceSnapshot();
        s.setId(id);
        s.setBusinessId(BIZ);
        s.setSnapshotType("MANUAL");
        s.setLabel("test");
        s.setMetricsJson(metricsJson);
        s.setCreatedAt(Instant.now());
        return s;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
