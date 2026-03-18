package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creative_optimization_recommendations")
public class CreativeOptimizationRecommendation {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID businessId;

    UUID creativeAssetId;

    @Column(nullable = false)
    String recommendationType;

    @Column(nullable = false)
    String priority;

    @Column(nullable = false)
    String title;

    @Column(nullable = false)
    String description;

    @Column(columnDefinition = "jsonb")
    String reasoningJson;

    String suggestedNextAction;

    @Column(nullable = false)
    String status;

    Instant appliedAt;

    Instant dismissedAt;

    @Column(columnDefinition = "jsonb")
    String metadataJson;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getCreativeAssetId() { return creativeAssetId; }
    public void setCreativeAssetId(UUID v) { creativeAssetId = v; }
    public String getRecommendationType() { return recommendationType; }
    public void setRecommendationType(String v) { recommendationType = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { priority = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getReasoningJson() { return reasoningJson; }
    public void setReasoningJson(String v) { reasoningJson = v; }
    public String getSuggestedNextAction() { return suggestedNextAction; }
    public void setSuggestedNextAction(String v) { suggestedNextAction = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant v) { appliedAt = v; }
    public Instant getDismissedAt() { return dismissedAt; }
    public void setDismissedAt(Instant v) { dismissedAt = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { metadataJson = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
