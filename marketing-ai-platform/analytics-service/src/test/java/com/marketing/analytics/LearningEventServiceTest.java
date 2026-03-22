package com.marketing.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.marketing.analytics.model.LearningEvent;
import com.marketing.analytics.repo.LearningEventRepository;
import com.marketing.analytics.service.LearningEventService;

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
class LearningEventServiceTest {

    @Mock LearningEventRepository eventRepo;
    @InjectMocks LearningEventService service;

    static final UUID BIZ = UUID.randomUUID();

    private LearningEvent buildEvent(String eventType, String severity, String dataJson, Instant createdAt) {
        LearningEvent e = new LearningEvent();
        e.setId(UUID.randomUUID());
        e.setBusinessId(BIZ);
        e.setEventType(eventType);
        e.setSourceEntityType("RECOMMENDATION");
        e.setSourceEntityId(UUID.randomUUID());
        e.setEventData(dataJson);
        e.setSeverity(severity);
        e.setCreatedAt(createdAt);
        return e;
    }

    @Nested class RecordTests {

        @Test void recordSavesEvent() {
            when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            LearningEvent result = service.record(BIZ, "BASELINE_CAPTURED", "RECOMMENDATION",
                    UUID.randomUUID(), Map.of("action", "APPLIED"), "INFO");

            assertNotNull(result);
            assertEquals(BIZ, result.getBusinessId());
            assertEquals("BASELINE_CAPTURED", result.getEventType());
            assertEquals("INFO", result.getSeverity());
            verify(eventRepo).save(any(LearningEvent.class));
        }

        @Test void recordWithNullSeverityDefaults() {
            when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            LearningEvent result = service.record(BIZ, "TEST_EVENT", "TEST", UUID.randomUUID(), Map.of(), null);

            assertEquals("INFO", result.getSeverity());
        }
    }

    @Nested class GetRecentEventsTests {

        @Test void getRecentWithDefaults() {
            List<LearningEvent> events = List.of(
                    buildEvent("BASELINE_CAPTURED", "INFO", "{}", Instant.now()),
                    buildEvent("RECOMMENDATION_OUTCOME", "INFO", "{}", Instant.now().minus(1, ChronoUnit.DAYS))
            );
            when(eventRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(BIZ), any())).thenReturn(events);

            List<Map<String, Object>> result = service.getRecentEvents(BIZ, 30, 50);

            assertEquals(2, result.size());
        }

        @Test void getRecentRespectLimit() {
            List<LearningEvent> events = List.of(
                    buildEvent("E1", "INFO", "{}", Instant.now()),
                    buildEvent("E2", "INFO", "{}", Instant.now()),
                    buildEvent("E3", "INFO", "{}", Instant.now())
            );
            when(eventRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(BIZ), any())).thenReturn(events);

            List<Map<String, Object>> result = service.getRecentEvents(BIZ, 30, 2);

            assertEquals(2, result.size());
        }

        @Test void emptyEvents() {
            when(eventRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(BIZ), any())).thenReturn(List.of());

            List<Map<String, Object>> result = service.getRecentEvents(BIZ, 30, 50);

            assertTrue(result.isEmpty());
        }
    }

    @Nested class GetEventsByTypeTests {

        @Test void filtersByType() {
            List<LearningEvent> events = List.of(
                    buildEvent("STRATEGY_STALE", "WARNING", "{}", Instant.now())
            );
            when(eventRepo.findByBusinessIdAndEventTypeOrderByCreatedAtDesc(BIZ, "STRATEGY_STALE")).thenReturn(events);

            List<Map<String, Object>> result = service.getEventsByType(BIZ, "STRATEGY_STALE");

            assertEquals(1, result.size());
            assertEquals("STRATEGY_STALE", result.get(0).get("eventType"));
        }
    }

    @Nested class LearningInsightsTests {

        @Test void insightsWithMixedEvents() {
            Instant now = Instant.now();
            List<LearningEvent> events = List.of(
                    buildEvent("RECOMMENDATION_OUTCOME", "INFO",
                            "{\"verdict\":\"POSITIVE\",\"recommendationType\":\"SCALE\",\"impactScore\":0.15}", now),
                    buildEvent("STRATEGY_STALE", "WARNING",
                            "{\"recommendedAction\":\"REFRESH\",\"freshnessScore\":45}", now.minus(2, ChronoUnit.DAYS)),
                    buildEvent("PERFORMANCE_DROP", "CRITICAL",
                            "{\"metric\":\"ROAS\",\"dropPercent\":25}", now.minus(5, ChronoUnit.DAYS)),
                    buildEvent("WINNER_EMERGED", "INFO",
                            "{\"assetId\":\"abc-123\"}", now.minus(3, ChronoUnit.DAYS))
            );
            when(eventRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(BIZ), any())).thenReturn(events);
            when(eventRepo.countByBusinessIdAndEventTypeAndCreatedAtAfter(eq(BIZ), eq("RECOMMENDATION_OUTCOME"), any())).thenReturn(1L);

            Map<String, Object> insights = service.getLearningInsights(BIZ);

            assertEquals(BIZ, insights.get("businessId"));
            assertEquals(4, insights.get("totalEvents30d"));
            assertEquals(1, insights.get("warnings30d"));
            assertEquals(1, insights.get("criticals30d"));
            assertNotNull(insights.get("recommendationOutcomes"));
            assertNotNull(insights.get("stalenessSignals"));
            assertNotNull(insights.get("learningNotes"));
            assertEquals(1L, insights.get("outcomesLast7d"));

            @SuppressWarnings("unchecked")
            List<String> outcomeSignals = (List<String>) insights.get("recommendationOutcomes");
            assertTrue(outcomeSignals.get(0).contains("SCALE"));

            @SuppressWarnings("unchecked")
            List<String> notes = (List<String>) insights.get("learningNotes");
            assertTrue(notes.stream().anyMatch(n -> n.contains("ROAS")));
        }

        @Test void insightsEmpty() {
            when(eventRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(BIZ), any())).thenReturn(List.of());
            when(eventRepo.countByBusinessIdAndEventTypeAndCreatedAtAfter(eq(BIZ), eq("RECOMMENDATION_OUTCOME"), any())).thenReturn(0L);

            Map<String, Object> insights = service.getLearningInsights(BIZ);

            assertEquals(0, insights.get("totalEvents30d"));
            assertEquals(0L, insights.get("outcomesLast7d"));
        }

        @Test void insightsEventCountsByType() {
            Instant now = Instant.now();
            List<LearningEvent> events = List.of(
                    buildEvent("BASELINE_CAPTURED", "INFO", "{}", now),
                    buildEvent("BASELINE_CAPTURED", "INFO", "{}", now.minus(1, ChronoUnit.DAYS)),
                    buildEvent("RECOMMENDATION_OUTCOME", "INFO",
                            "{\"verdict\":\"NEGATIVE\",\"recommendationType\":\"STOP\",\"impactScore\":-0.1}", now)
            );
            when(eventRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(BIZ), any())).thenReturn(events);
            when(eventRepo.countByBusinessIdAndEventTypeAndCreatedAtAfter(eq(BIZ), eq("RECOMMENDATION_OUTCOME"), any())).thenReturn(1L);

            Map<String, Object> insights = service.getLearningInsights(BIZ);

            @SuppressWarnings("unchecked")
            Map<String, Long> counts = (Map<String, Long>) insights.get("eventCounts");
            assertEquals(2L, counts.get("BASELINE_CAPTURED"));
            assertEquals(1L, counts.get("RECOMMENDATION_OUTCOME"));
        }
    }
}
