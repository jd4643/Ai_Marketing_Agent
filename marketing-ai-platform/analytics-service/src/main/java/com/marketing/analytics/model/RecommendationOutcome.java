package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommendation_outcomes")
public class RecommendationOutcome {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID recommendationId;

    @Column(nullable = false)
    UUID businessId;

    @Column(nullable = false)
    String actionTaken;

    @Column(nullable = false)
    Instant actionDate;

    @Column(nullable = false, columnDefinition = "jsonb")
    String baselineSnapshot;

    @Column(nullable = false)
    int evaluationWindowDays;

    @Column(columnDefinition = "jsonb")
    String outcomeSnapshot;

    Instant evaluationDate;

    Double impactScore;

    @Column(nullable = false)
    String outcomeVerdict;

    @Column(columnDefinition = "jsonb")
    String deltaJson;

    String notes;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getRecommendationId() { return recommendationId; }
    public void setRecommendationId(UUID v) { recommendationId = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String v) { actionTaken = v; }
    public Instant getActionDate() { return actionDate; }
    public void setActionDate(Instant v) { actionDate = v; }
    public String getBaselineSnapshot() { return baselineSnapshot; }
    public void setBaselineSnapshot(String v) { baselineSnapshot = v; }
    public int getEvaluationWindowDays() { return evaluationWindowDays; }
    public void setEvaluationWindowDays(int v) { evaluationWindowDays = v; }
    public String getOutcomeSnapshot() { return outcomeSnapshot; }
    public void setOutcomeSnapshot(String v) { outcomeSnapshot = v; }
    public Instant getEvaluationDate() { return evaluationDate; }
    public void setEvaluationDate(Instant v) { evaluationDate = v; }
    public Double getImpactScore() { return impactScore; }
    public void setImpactScore(Double v) { impactScore = v; }
    public String getOutcomeVerdict() { return outcomeVerdict; }
    public void setOutcomeVerdict(String v) { outcomeVerdict = v; }
    public String getDeltaJson() { return deltaJson; }
    public void setDeltaJson(String v) { deltaJson = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
