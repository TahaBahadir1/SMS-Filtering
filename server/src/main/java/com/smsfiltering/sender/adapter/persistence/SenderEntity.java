package com.smsfiltering.sender.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "senders")
public class SenderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "sender_value", length = 32, nullable = false, unique = true)
    private String senderValue;

    @Column(name = "risk_score", precision = 3, scale = 2, nullable = false)
    private BigDecimal riskScore;

    @Column(name = "risk_level", length = 20, nullable = false)
    private String riskLevel;

    @Column(name = "is_finalized", nullable = false)
    private Boolean isFinalized;

    @Column(name = "first_seen", nullable = false)
    private OffsetDateTime firstSeen;

    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated;

    public SenderEntity() {
    }

    public SenderEntity(Long id, String senderValue, BigDecimal riskScore, String riskLevel, Boolean isFinalized, OffsetDateTime firstSeen, OffsetDateTime lastUpdated) {
        this.id = id;
        this.senderValue = senderValue;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.isFinalized = isFinalized;
        this.firstSeen = firstSeen;
        this.lastUpdated = lastUpdated;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSenderValue() {
        return senderValue;
    }

    public void setSenderValue(String senderValue) {
        this.senderValue = senderValue;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getIsFinalized() {
        return isFinalized;
    }

    public void setIsFinalized(Boolean isFinalized) {
        this.isFinalized = isFinalized;
    }

    public OffsetDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(OffsetDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    public OffsetDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(OffsetDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
