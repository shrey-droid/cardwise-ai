package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.RewardRule;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RewardCalculationService {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal calculateAnnualReward(
            RewardRule rule,
            BigDecimal monthlySpending
    ) {
        if (rule == null || monthlySpending == null
                || monthlySpending.signum() < 0) {
            throw new IllegalArgumentException(
                    "Valid reward rule and non-negative spending required"
            );
        }

        BigDecimal annualSpending = monthlySpending.multiply(TWELVE);

        if (rule.getSpendingCap() == null) {
            return annualSpending.multiply(rule.getRewardRate())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }

        BigDecimal annualCap;

        if ("MONTHLY".equals(rule.getCapPeriod())) {
            annualCap = rule.getSpendingCap().multiply(TWELVE);
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
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
