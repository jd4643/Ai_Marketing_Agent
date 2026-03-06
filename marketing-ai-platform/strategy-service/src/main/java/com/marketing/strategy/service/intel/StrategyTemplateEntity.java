package com.marketing.strategy.service.intel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "strategy_template")
public class StrategyTemplateEntity {
    @Id
    private UUID id;
    @Column(name = "template_key", nullable = false, unique = true)
    private String templateKey;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String description;
    @Column(name = "channel_mix_json", nullable = false, columnDefinition = "jsonb")
    private String channelMixJson;
    @Column(name = "campaign_structure_json", nullable = false, columnDefinition = "jsonb")
    private String campaignStructureJson;
    @Column(name = "creative_angles_json", nullable = false, columnDefinition = "jsonb")
    private String creativeAnglesJson;
    @Column(name = "kpi_targets_json", nullable = false, columnDefinition = "jsonb")
    private String kpiTargetsJson;
    @Column(name = "risk_factors_json", nullable = false, columnDefinition = "jsonb")
    private String riskFactorsJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getChannelMixJson() {
        return channelMixJson;
    }

    public void setChannelMixJson(String channelMixJson) {
        this.channelMixJson = channelMixJson;
    }

    public String getCampaignStructureJson() {
        return campaignStructureJson;
    }

    public void setCampaignStructureJson(String campaignStructureJson) {
        this.campaignStructureJson = campaignStructureJson;
    }

    public String getCreativeAnglesJson() {
        return creativeAnglesJson;
    }

    public void setCreativeAnglesJson(String creativeAnglesJson) {
        this.creativeAnglesJson = creativeAnglesJson;
    }

    public String getKpiTargetsJson() {
        return kpiTargetsJson;
    }

    public void setKpiTargetsJson(String kpiTargetsJson) {
        this.kpiTargetsJson = kpiTargetsJson;
    }

    public String getRiskFactorsJson() {
        return riskFactorsJson;
    }

    public void setRiskFactorsJson(String riskFactorsJson) {
        this.riskFactorsJson = riskFactorsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
