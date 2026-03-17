package com.marketing.analytics.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ad_platform_insights")
public class AdPlatformInsight {
    @Id private UUID id;
    @Column(nullable = false) private UUID businessId;
    @Column(nullable = false) private UUID connectionId;
    @Column(nullable = false) private String platform;
    @Column(nullable = false) private String externalAdId;
    @Column(nullable = false) private LocalDate dateStart;
    @Column(nullable = false) private LocalDate dateStop;
    private Long impressions;
    private Long clicks;
    private BigDecimal spend;
    private Long reach;
    private BigDecimal ctr;
    private BigDecimal cpc;
    private BigDecimal cpm;
    private Long conversions;
    private BigDecimal revenue;
    private BigDecimal roas;
    @Column(columnDefinition = "jsonb") private String actionsJson;
    @Column(columnDefinition = "jsonb") private String actionValuesJson;
    @Column(columnDefinition = "jsonb") private String rawJson;
    @Column(nullable = false) private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID v) { id = v; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID v) { businessId = v; }
    public UUID getConnectionId() { return connectionId; }
    public void setConnectionId(UUID v) { connectionId = v; }
    public String getPlatform() { return platform; }
    public void setPlatform(String v) { platform = v; }
    public String getExternalAdId() { return externalAdId; }
    public void setExternalAdId(String v) { externalAdId = v; }
    public LocalDate getDateStart() { return dateStart; }
    public void setDateStart(LocalDate v) { dateStart = v; }
    public LocalDate getDateStop() { return dateStop; }
    public void setDateStop(LocalDate v) { dateStop = v; }
    public Long getImpressions() { return impressions; }
    public void setImpressions(Long v) { impressions = v; }
    public Long getClicks() { return clicks; }
    public void setClicks(Long v) { clicks = v; }
    public BigDecimal getSpend() { return spend; }
    public void setSpend(BigDecimal v) { spend = v; }
    public Long getReach() { return reach; }
    public void setReach(Long v) { reach = v; }
    public BigDecimal getCtr() { return ctr; }
    public void setCtr(BigDecimal v) { ctr = v; }
    public BigDecimal getCpc() { return cpc; }
    public void setCpc(BigDecimal v) { cpc = v; }
    public BigDecimal getCpm() { return cpm; }
    public void setCpm(BigDecimal v) { cpm = v; }
    public Long getConversions() { return conversions; }
    public void setConversions(Long v) { conversions = v; }
    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal v) { revenue = v; }
    public BigDecimal getRoas() { return roas; }
    public void setRoas(BigDecimal v) { roas = v; }
    public String getActionsJson() { return actionsJson; }
    public void setActionsJson(String v) { actionsJson = v; }
    public String getActionValuesJson() { return actionValuesJson; }
    public void setActionValuesJson(String v) { actionValuesJson = v; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String v) { rawJson = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { createdAt = v; }
}
