package com.marketing.analytics.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_metrics")
public class CampaignMetric {
    @Id
    UUID id;
    @Column(nullable = false)
    UUID businessId;
    @Column(nullable = false)
    String platform;
    @Column(nullable = false)
    BigDecimal spend;
    Long impressions;
    Long clicks;
    Long conversions;
    BigDecimal revenue;
    BigDecimal ctr;
    BigDecimal cpc;
    BigDecimal cpa;
    BigDecimal roas;
    @Column(nullable = false)
    Instant recordedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public void setBusinessId(UUID b) {
        businessId = b;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String p) {
        platform = p;
    }

    public BigDecimal getSpend() {
        return spend;
    }

    public void setSpend(BigDecimal s) {
        spend = s;
    }

    public Long getImpressions() {
        return impressions;
    }

    public void setImpressions(Long v) {
        impressions = v;
    }

    public Long getClicks() {
        return clicks;
    }

    public void setClicks(Long v) {
        clicks = v;
    }

    public Long getConversions() {
        return conversions;
    }

    public void setConversions(Long v) {
        conversions = v;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal v) {
        revenue = v;
    }

    public BigDecimal getCtr() {
        return ctr;
    }

    public void setCtr(BigDecimal v) {
        ctr = v;
    }

    public BigDecimal getCpc() {
        return cpc;
    }

    public void setCpc(BigDecimal v) {
        cpc = v;
    }

    public BigDecimal getCpa() {
        return cpa;
    }

    public void setCpa(BigDecimal v) {
        cpa = v;
    }

    public BigDecimal getRoas() {
        return roas;
    }

    public void setRoas(BigDecimal v) {
        roas = v;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant v) {
        recordedAt = v;
    }
}
