package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_events")
public class LearningEvent {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID businessId;

    @Column(nullable = false)
    String eventType;

    String sourceEntityType;

    UUID sourceEntityId;

    @Column(nullable = false, columnDefinition = "jsonb")
    String eventData;

    @Column(nullable = false)
    String severity;

    @Column(nullable = false)
    Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { eventType = v; }
    public String getSourceEntityType() { return sourceEntityType; }
    public void setSourceEntityType(String v) { sourceEntityType = v; }
    public UUID getSourceEntityId() { return sourceEntityId; }
    public void setSourceEntityId(UUID v) { sourceEntityId = v; }
    public String getEventData() { return eventData; }
    public void setEventData(String v) { eventData = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { severity = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
