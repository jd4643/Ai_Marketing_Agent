package com.marketing.analytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketing.analytics.model.LearningEvent;
import com.marketing.analytics.repo.LearningEventRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central event log for all closed-loop learning signals.
 * Records, queries, and aggregates learning events across the system.
 */
@Service
public class LearningEventService {

    private static final Logger log = LoggerFactory.getLogger(LearningEventService.class);
    private final LearningEventRepository eventRepo;
    private final ObjectMapper om = new ObjectMapper();

    public LearningEventService(LearningEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    /**
     * Record a learning event.
     */
    @Transactional
    public LearningEvent record(UUID businessId, String eventType, String sourceEntityType,
                                UUID sourceEntityId, Map<String, Object> data, String severity) {
        LearningEvent event = new LearningEvent();
        event.setId(UUID.randomUUID());
        event.setBusinessId(businessId);
        event.setEventType(eventType);
        event.setSourceEntityType(sourceEntityType);
        event.setSourceEntityId(sourceEntityId);
        event.setEventData(toJsonSafe(data));
        event.setSeverity(severity != null ? severity : "INFO");
        event.setCreatedAt(Instant.now());
        eventRepo.save(event);

        log.debug("Recorded learning event type={} source={}:{} business={}",
                eventType, sourceEntityType, sourceEntityId, businessId);
        return event;
    }

    /**
     * Get recent learning events for a business.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentEvents(UUID businessId, int days, int limit) {
        Instant since = Instant.now().minus(days > 0 ? days : 30, ChronoUnit.DAYS);
        List<LearningEvent> events = eventRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(businessId, since);
        return events.stream()
                .limit(limit > 0 ? limit : 50)
                .map(this::eventToMap)
                .toList();
    }

    /**
     * Get events filtered by type.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEventsByType(UUID businessId, String eventType) {
        return eventRepo.findByBusinessIdAndEventTypeOrderByCreatedAtDesc(businessId, eventType)
                .stream().map(this::eventToMap).toList();
    }

    /**
     * Aggregate learning events into high-level insights for a business.
     * Used by strategy-service to enrich prompts with learning context.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLearningInsights(UUID businessId) {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        Map<String, Object> insights = new LinkedHashMap<>();
        insights.put("businessId", businessId);

        // Count events by type (last 30 days)
        List<LearningEvent> recent = eventRepo.findByBusinessIdAndCreatedAtAfterOrderByCreatedAtDesc(businessId, thirtyDaysAgo);

        Map<String, Long> eventCounts = new LinkedHashMap<>();
        int warningCount = 0;
        int criticalCount = 0;
        for (LearningEvent e : recent) {
            eventCounts.merge(e.getEventType(), 1L, Long::sum);
            if ("WARNING".equals(e.getSeverity())) warningCount++;
            if ("CRITICAL".equals(e.getSeverity())) criticalCount++;
        }
        insights.put("eventCounts", eventCounts);
        insights.put("totalEvents30d", recent.size());
        insights.put("warnings30d", warningCount);
        insights.put("criticals30d", criticalCount);

        // Extract recommendation outcome signals
        List<String> outcomeSignals = new ArrayList<>();
        List<String> stalenessSignals = new ArrayList<>();
        List<String> learningNotes = new ArrayList<>();

        for (LearningEvent e : recent) {
            Map<String, Object> data = parseJsonSafe(e.getEventData());
            switch (e.getEventType()) {
                case "RECOMMENDATION_OUTCOME" -> {
                    String verdict = (String) data.get("verdict");
                    String recType = (String) data.get("recommendationType");
                    Object impact = data.get("impactScore");
                    outcomeSignals.add(recType + " → " + verdict + " (impact=" + impact + ")");
                }
                case "STRATEGY_STALE" -> {
                    String action = (String) data.get("recommendedAction");
                    Object freshness = data.get("freshnessScore");
                    stalenessSignals.add("Freshness=" + freshness + " → " + action);
                }
                case "PERFORMANCE_DROP" -> {
                    String metric = (String) data.get("metric");
                    Object drop = data.get("dropPercent");
                    learningNotes.add("Performance drop: " + metric + " down " + drop + "%");
                }
                case "WINNER_EMERGED" -> {
                    Object assetId = data.get("assetId");
                    learningNotes.add("New winner asset emerged: " + assetId);
                }
            }
        }

        insights.put("recommendationOutcomes", outcomeSignals);
        insights.put("stalenessSignals", stalenessSignals);
        insights.put("learningNotes", learningNotes);

        // Recent 7-day summary for quick context
        long recentCount = eventRepo.countByBusinessIdAndEventTypeAndCreatedAtAfter(
                businessId, "RECOMMENDATION_OUTCOME", sevenDaysAgo);
        insights.put("outcomesLast7d", recentCount);

        return insights;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    Map<String, Object> eventToMap(LearningEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("businessId", e.getBusinessId());
        m.put("eventType", e.getEventType());
        m.put("sourceEntityType", e.getSourceEntityType());
        m.put("sourceEntityId", e.getSourceEntityId());
        m.put("eventData", parseJsonSafe(e.getEventData()));
        m.put("severity", e.getSeverity());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    private Map<String, Object> parseJsonSafe(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return om.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of("raw", json);
        }
    }

    private String toJsonSafe(Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
