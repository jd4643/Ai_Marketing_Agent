package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ad_platform_connections")
public class AdPlatformConnection {
    @Id private UUID id;
    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private String platform;
    private String externalBusinessId;
    @Column(nullable = false) private String externalAccountId;
    private String connectionName;
    private String accessTokenEncrypted;
    private String refreshTokenEncrypted;
    private Instant tokenExpiresAt;
    @Column(columnDefinition = "jsonb") private String scopesJson;
    @Column(nullable = false) private String status;
    private Instant lastSyncedAt;
    @Column(columnDefinition = "jsonb") private String metadataJson;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { platform = v; }
    public String getExternalBusinessId() { return externalBusinessId; }
    public void setExternalBusinessId(String v) { externalBusinessId = v; }
    public String getExternalAccountId() { return externalAccountId; }
    public void setExternalAccountId(String v) { externalAccountId = v; }
    public String getConnectionName() { return connectionName; }
    public void setConnectionName(String v) { connectionName = v; }
    public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
    public void setAccessTokenEncrypted(String v) { accessTokenEncrypted = v; }
    public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
    public void setRefreshTokenEncrypted(String v) { refreshTokenEncrypted = v; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant v) { tokenExpiresAt = v; }
    public String getScopesJson() { return scopesJson; }
    public void setScopesJson(String v) { scopesJson = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant v) { lastSyncedAt = v; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String v) { metadataJson = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}
