package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "creative_asset_platform_mapping")
public class CreativeAssetPlatformMapping {
    @Id private UUID id;
    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID creativeAssetId;
    @Column(nullable = false) private UUID connectionId;
    @Column(nullable = false) private String platform;
    private String externalAdId;
    private String externalCreativeId;
    @Column(nullable = false) private String mappingMethod;
    private BigDecimal confidenceScore;
    @Column(columnDefinition = "jsonb") private String metadataJson;
    @Column(nullable = false) private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getCreativeAssetId() { return creativeAssetId; }
    public void setCreativeAssetId(UUID v) { creativeAssetId = v; }
    public UUID getConnectionId() { return connectionId; }
    public void setConnectionId(UUID v) { connectionId = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { platform = v; }
    public String getExternalAdId() { return externalAdId; }
    public void setExternalAdId(String v) { externalAdId = v; }
    public String getExternalCreativeId() { return externalCreativeId; }
    public void setExternalCreativeId(String v) { externalCreativeId = v; }
    public String getMappingMethod() { return mappingMethod; }
    public void setMappingMethod(String v) { mappingMethod = v; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal v) { confidenceScore = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { metadataJson = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
