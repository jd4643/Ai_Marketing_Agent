package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "performance_snapshots")
public class PerformanceSnapshot {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID businessId;

    UUID planId;

    @Column(nullable = false)
    String snapshotType;

    String label;

    @Column(nullable = false, columnDefinition = "jsonb")
    String metricsJson;

    @Column(columnDefinition = "jsonb")
    String insightsJson;

    @Column(nullable = false)
    Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID v) { planId = v; }
    public String getSnapshotType() { return snapshotType; }
    public void setSnapshotType(String v) { snapshotType = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { label = v; }
    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String v) { metricsJson = v; }
    public String getInsightsJson() { return insightsJson; }
    public void setInsightsJson(String v) { insightsJson = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
