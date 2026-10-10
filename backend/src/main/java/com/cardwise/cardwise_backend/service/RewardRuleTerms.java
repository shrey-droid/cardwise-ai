package com.cardwise.cardwise_backend.service;

import java.math.BigDecimal;

/**
 * Read-only reward terms used by the calculation. Implemented by both the
 * RewardRule entity and EffectiveRewardRule, so RewardCalculationService can
 * later accept this type with no change to its formulas.
 */
public interface RewardRuleTerms {

    BigDecimal getRewardRate();

    BigDecimal getBaseRewardRate();

    BigDecimal getSpendingCap();

    String getCapPeriod();
}
