package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "execution_tasks")
public class ExecutionTask {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID planId;

    @Column(nullable = false)
    UUID businessId;

    UUID recommendationId;

    UUID dependsOnTaskId;

    @Column(nullable = false)
    String taskType;

    @Column(nullable = false)
    String name;

    String description;

    @Column(columnDefinition = "jsonb")
    String inputJson;

    @Column(columnDefinition = "jsonb")
    String outputJson;

    @Column(nullable = false)
    String status;

    @Column(nullable = false)
    int priority;

    @Column(nullable = false)
    int sequenceOrder;

    @Column(nullable = false)
    int maxRetries;

    @Column(nullable = false)
    int retryCount;

    String errorMessage;

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
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID v) { planId = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getRecommendationId() { return recommendationId; }
    public void setRecommendationId(UUID v) { recommendationId = v; }
    public UUID getDependsOnTaskId() { return dependsOnTaskId; }
    public void setDependsOnTaskId(UUID v) { dependsOnTaskId = v; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String v) { taskType = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getInputJson() { return inputJson; }
    public void setInputJson(String v) { inputJson = v; }
    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String v) { outputJson = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public int getPriority() { return priority; }
    public void setPriority(int v) { priority = v; }
    public int getSequenceOrder() { return sequenceOrder; }
    public void setSequenceOrder(int v) { sequenceOrder = v; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int v) { maxRetries = v; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int v) { retryCount = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v; }
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
