package com.marketing.strategy.service.intel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "strategy_run_intel")
public class StrategyRunIntelEntity {
  @Id
  private UUID id;
  @Column(name = "request_id", nullable = false)
  private UUID requestId;
  @Column(name = "business_id", nullable = false)
  private UUID businessId;
  @Column(nullable = false)
  private String objective;
  @Column(name = "monthly_budget", nullable = false)
  private BigDecimal monthlyBudget;
  @Column(name = "chosen_template_key", nullable = false)
  private String chosenTemplateKey;
  @Column(name = "decision_path_json", nullable = false)
  private String decisionPathJson;
  @Column(name = "confidence_score", nullable = false)
  private Integer confidenceScore;
  @Column(name = "score_breakdown_json", nullable = false)
  private String scoreBreakdownJson;
  @Column(name = "similarity_match_json")
  private String similarityMatchJson;
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getRequestId() { return requestId; }
  public void setRequestId(UUID requestId) { this.requestId = requestId; }
  public UUID getBusinessId() { return businessId; }
  public void setBusinessId(UUID businessId) { this.businessId = businessId; }
  public String getObjective() { return objective; }
  public void setObjective(String objective) { this.objective = objective; }
  public BigDecimal getMonthlyBudget() { return monthlyBudget; }
  public void setMonthlyBudget(BigDecimal monthlyBudget) { this.monthlyBudget = monthlyBudget; }
  public String getChosenTemplateKey() { return chosenTemplateKey; }
  public void setChosenTemplateKey(String chosenTemplateKey) { this.chosenTemplateKey = chosenTemplateKey; }
  public String getDecisionPathJson() { return decisionPathJson; }
  public void setDecisionPathJson(String decisionPathJson) { this.decisionPathJson = decisionPathJson; }
  public Integer getConfidenceScore() { return confidenceScore; }
  public void setConfidenceScore(Integer confidenceScore) { this.confidenceScore = confidenceScore; }
  public String getScoreBreakdownJson() { return scoreBreakdownJson; }
  public void setScoreBreakdownJson(String scoreBreakdownJson) { this.scoreBreakdownJson = scoreBreakdownJson; }
  public String getSimilarityMatchJson() { return similarityMatchJson; }
  public void setSimilarityMatchJson(String similarityMatchJson) { this.similarityMatchJson = similarityMatchJson; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
