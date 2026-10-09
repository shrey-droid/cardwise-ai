package com.cardwise.cardwise_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_cards")
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_name", nullable = false)
    private String cardName;

    @Column(nullable = false)
    private String issuer;

    @Column(name = "reward_type", nullable = false)
    private String rewardType;

    @Column(name = "annual_fee", nullable = false)
    private BigDecimal annualFee;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "minimum_personal_income", precision = 12, scale = 2)
    private BigDecimal minimumPersonalIncome;

    @Column(name = "minimum_household_income", precision = 12, scale = 2)
    private BigDecimal minimumHouseholdIncome;

    @Column(name = "official_url")
    private String officialUrl;

    @Column(name = "last_verified_at")
    private LocalDate lastVerifiedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public CreditCard() {}

    public Long getId() { return id; }

    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getRewardType() { return rewardType; }
    public void setRewardType(String rewardType) { this.rewardType = rewardType; }

    public BigDecimal getAnnualFee() { return annualFee; }
    public void setAnnualFee(BigDecimal annualFee) { this.annualFee = annualFee; }

    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }

    public BigDecimal getMinimumPersonalIncome() {
        return minimumPersonalIncome;
    }
    public void setMinimumPersonalIncome(BigDecimal minimumPersonalIncome) {
        this.minimumPersonalIncome = minimumPersonalIncome;
    }

    public BigDecimal getMinimumHouseholdIncome() {
        return minimumHouseholdIncome;
    }
    public void setMinimumHouseholdIncome(BigDecimal minimumHouseholdIncome) {
        this.minimumHouseholdIncome = minimumHouseholdIncome;
    }

    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String officialUrl) {
        this.officialUrl = officialUrl;
    }

    public LocalDate getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(LocalDate lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
}