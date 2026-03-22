package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "strategy_effectiveness")
public class StrategyEffectiveness {

    @Id
    UUID id;

    @Column(nullable = false)
    UUID strategyRunId;

    @Column(nullable = false)
    UUID businessId;

    @Column(nullable = false)
    String evaluationType;

    @Column(nullable = false, columnDefinition = "jsonb")
    String metricsAtEvaluation;

    @Column(nullable = false)
    double freshnessScore;

    @Column(columnDefinition = "jsonb")
    String stalenessSignals;

    @Column(nullable = false)
    String recommendedAction;

    @Column(nullable = false)
    Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getStrategyRunId() { return strategyRunId; }
    public void setStrategyRunId(UUID v) { strategyRunId = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public String getEvaluationType() { return evaluationType; }
    public void setEvaluationType(String v) { evaluationType = v; }
    public String getMetricsAtEvaluation() { return metricsAtEvaluation; }
    public void setMetricsAtEvaluation(String v) { metricsAtEvaluation = v; }
    public double getFreshnessScore() { return freshnessScore; }
    public void setFreshnessScore(double v) { freshnessScore = v; }
    public String getStalenessSignals() { return stalenessSignals; }
    public void setStalenessSignals(String v) { stalenessSignals = v; }
    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String v) { recommendedAction = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
