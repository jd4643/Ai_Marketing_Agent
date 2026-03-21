package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_plans")
public class ExecutionPlan {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID businessId;

    UUID strategyRunId;

    @Column(nullable = false)
    String name;

    String description;

    @Column(nullable = false)
    String sourceType;

    @Column(columnDefinition = "jsonb")
    String sourceJson;

    @Column(nullable = false)
    String status;

    @Column(nullable = false)
    int totalTasks;

    @Column(nullable = false)
    int completedTasks;

    @Column(nullable = false)
    int failedTasks;

    @Column(nullable = false)
    int skippedTasks;

    @Column(nullable = false)
    int version;

    Instant startedAt;

    Instant completedAt;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getStrategyRunId() { return strategyRunId; }
    public void setStrategyRunId(UUID v) { strategyRunId = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String v) { sourceType = v; }
    public String getSourceJson() { return sourceJson; }
    public void setSourceJson(String v) { sourceJson = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public int getTotalTasks() { return totalTasks; }
    public void setTotalTasks(int v) { totalTasks = v; }
    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int v) { completedTasks = v; }
    public int getFailedTasks() { return failedTasks; }
    public void setFailedTasks(int v) { failedTasks = v; }
    public int getSkippedTasks() { return skippedTasks; }
    public void setSkippedTasks(int v) { skippedTasks = v; }
    public int getVersion() { return version; }
    public void setVersion(int v) { version = v; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { startedAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { completedAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
