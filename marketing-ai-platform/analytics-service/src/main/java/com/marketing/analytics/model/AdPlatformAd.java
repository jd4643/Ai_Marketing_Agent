package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ad_platform_ads")
public class AdPlatformAd {
    @Id private UUID id;
    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID connectionId;
    @Column(nullable = false) private String platform;
    private String externalCampaignId;
    private String externalAdGroupId;
    @Column(nullable = false) private String externalAdId;
    private String externalCreativeId;
    private String campaignName;
    private String adGroupName;
    private String adName;
    private String creativeName;
    private String status;
    private String effectiveStatus;
    private String objective;
    @Column(columnDefinition = "jsonb") private String rawJson;
    @Column(nullable = false) private Instant lastSeenAt;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getConnectionId() { return connectionId; }
    public void setConnectionId(UUID v) { connectionId = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { platform = v; }
    public String getExternalCampaignId() { return externalCampaignId; }
    public void setExternalCampaignId(String v) { externalCampaignId = v; }
    public String getExternalAdGroupId() { return externalAdGroupId; }
    public void setExternalAdGroupId(String v) { externalAdGroupId = v; }
    public String getExternalAdId() { return externalAdId; }
    public void setExternalAdId(String v) { externalAdId = v; }
    public String getExternalCreativeId() { return externalCreativeId; }
    public void setExternalCreativeId(String v) { externalCreativeId = v; }
    public String getCampaignName() { return campaignName; }
    public void setCampaignName(String v) { campaignName = v; }
    public String getAdGroupName() { return adGroupName; }
    public void setAdGroupName(String v) { adGroupName = v; }
    public String getAdName() { return adName; }
    public void setAdName(String v) { adName = v; }
    public String getCreativeName() { return creativeName; }
    public void setCreativeName(String v) { creativeName = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getEffectiveStatus() { return effectiveStatus; }
    public void setEffectiveStatus(String v) { effectiveStatus = v; }
    public String getObjective() { return objective; }
    public void setObjective(String v) { objective = v; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String v) { rawJson = v; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant v) { lastSeenAt = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
