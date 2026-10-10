package com.cardwise.cardwise_backend.service;

import java.math.BigDecimal;

public record EffectiveRewardRule(
        String spendingCategory,
        BigDecimal rewardRate,
        BigDecimal baseRewardRate,
        BigDecimal spendingCap,
        String capPeriod
) implements RewardRuleTerms {

    @Override
    public BigDecimal getRewardRate() {
        return rewardRate;
    }

    @Override
    public BigDecimal getBaseRewardRate() {
        return baseRewardRate;
    }

    @Override
    public BigDecimal getSpendingCap() {
        return spendingCap;
    }

    @Override
    public String getCapPeriod() {
        return capPeriod;
    }
}
