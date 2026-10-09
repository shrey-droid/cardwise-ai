package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.RewardRule;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RewardCalculationService {

    public BigDecimal calculateAnnualReward(
            RewardRule rule,
            BigDecimal monthlySpending
    ) {
        return calculateAnnualRewardPrecise(rule, monthlySpending)
            .setScale(2, RoundingMode.HALF_UP);
    }

            public BigDecimal calculateAnnualRewardPrecise(
                RewardRule rule,
                BigDecimal monthlySpending
            ) {
            if (rule == null || monthlySpending == null
                || monthlySpending.signum() < 0) {
                throw new IllegalArgumentException(
                    "Valid reward rule and non-negative spending required"
                );
            }

            BigDecimal annualSpending =
                monthlySpending.multiply(BigDecimal.valueOf(12));

            if (rule.getSpendingCap() == null) {
                return annualSpending
                    .multiply(rule.getRewardRate())
                    .divide(BigDecimal.valueOf(100));
            }

            BigDecimal annualCap;

            if ("MONTHLY".equals(rule.getCapPeriod())) {
                annualCap = rule.getSpendingCap()
                    .multiply(BigDecimal.valueOf(12));
            } else if ("ANNUAL".equals(rule.getCapPeriod())) {
                annualCap = rule.getSpendingCap();
            } else {
                throw new IllegalArgumentException(
                    "Unsupported cap period: " + rule.getCapPeriod()
                );
            }

            if (rule.getBaseRewardRate() == null) {
                throw new IllegalArgumentException(
                    "Fallback rate required for capped rules"
                );
            }

            BigDecimal eligible = annualSpending.min(annualCap);
            BigDecimal excess = annualSpending.subtract(eligible);

            return eligible.multiply(rule.getRewardRate())
                .add(excess.multiply(rule.getBaseRewardRate()))
                .divide(BigDecimal.valueOf(100));
            }
}
