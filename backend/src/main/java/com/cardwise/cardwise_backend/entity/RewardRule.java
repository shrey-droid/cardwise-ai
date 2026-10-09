package com.cardwise.cardwise_backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "reward_rules")
public class RewardRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_card_id", nullable = false)
    private Long creditCardId;

    @Column(name = "spending_category", nullable = false)
    private String spendingCategory;

    @Column(name = "reward_rate", nullable = false)
    private BigDecimal rewardRate;

    @Column(name = "base_reward_rate", precision = 7, scale = 4)
    private BigDecimal baseRewardRate;

    @Column(name = "spending_cap", precision = 12, scale = 2)
    private BigDecimal spendingCap;

    @Column(name = "cap_period", length = 20)
    private String capPeriod;

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

    public RewardRule() {}

    public Long getId() {
        return id;
    }

    public Long getCreditCardId() {
        return creditCardId;
    }

    public void setCreditCardId(Long creditCardId) {
        this.creditCardId = creditCardId;
    }

    public String getSpendingCategory() {
        return spendingCategory;
    }

    public void setSpendingCategory(String spendingCategory) {
        this.spendingCategory = spendingCategory;
    }

    public BigDecimal getRewardRate() {
        return rewardRate;
    }

    public void setRewardRate(BigDecimal rewardRate) {
        this.rewardRate = rewardRate;
    }

    public BigDecimal getBaseRewardRate() {
        return baseRewardRate;
    }

    public void setBaseRewardRate(BigDecimal baseRewardRate) {
        this.baseRewardRate = baseRewardRate;
    }

    public BigDecimal getSpendingCap() {
        return spendingCap;
    }

    public void setSpendingCap(BigDecimal spendingCap) {
        this.spendingCap = spendingCap;
    }

    public String getCapPeriod() {
        return capPeriod;
    }

    public void setCapPeriod(String capPeriod) {
        this.capPeriod = capPeriod;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }
}