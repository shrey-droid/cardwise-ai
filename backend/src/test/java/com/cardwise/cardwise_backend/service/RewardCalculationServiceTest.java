package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.RewardRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RewardCalculationServiceTest {

    private final RewardCalculationService calculator =
            new RewardCalculationService();

    private RewardRule entity(
            String rate, String baseRate, String cap, String period) {
        RewardRule rule = new RewardRule();
        rule.setRewardRate(new BigDecimal(rate));
        rule.setBaseRewardRate(baseRate == null ? null : new BigDecimal(baseRate));
        rule.setSpendingCap(cap == null ? null : new BigDecimal(cap));
        rule.setCapPeriod(period);
        return rule;
    }

    private EffectiveRewardRule effective(RewardRule rule) {
        return new EffectiveRewardRule(
                "GROCERIES",
                rule.getRewardRate(),
                rule.getBaseRewardRate(),
                rule.getSpendingCap(),
                rule.getCapPeriod()
        );
    }

    @Test
    void uncappedEntityRuleIsUnchanged() {
        // 500 * 12 * 2% = 120.00
        assertEquals(new BigDecimal("120.00"), calculator.calculateAnnualReward(
                entity("2", null, null, null), new BigDecimal("500")));
    }

    @Test
    void monthlyCappedEntityRuleIsUnchanged() {
        // cap 300/month = 3600/year; spend 500/month = 6000/year
        // 3600 * 4% + 2400 * 1% = 168.00
        assertEquals(new BigDecimal("168.00"), calculator.calculateAnnualReward(
                entity("4", "1", "300", "MONTHLY"), new BigDecimal("500")));
    }

    @Test
    void annualCappedEntityRuleIsUnchanged() {
        // 1000 * 5% + 5000 * 1% = 100.00
        assertEquals(new BigDecimal("100.00"), calculator.calculateAnnualReward(
                entity("5", "1", "1000", "ANNUAL"), new BigDecimal("500")));
    }

    @Test
    void roundingIsUnchanged() {
        // 33.33 * 12 * 1.5% = 5.9994 -> 6.00
        assertEquals(new BigDecimal("6.00"), calculator.calculateAnnualReward(
                entity("1.5", null, null, null), new BigDecimal("33.33")));
    }

    @Test
    void entityAndEffectiveRulesGiveIdenticalResults() {
        RewardRule[] rules = {
                entity("2", null, null, null),
                entity("4", "1", "300", "MONTHLY"),
                entity("5", "1", "1000", "ANNUAL")
        };

        for (RewardRule rule : rules) {
            assertEquals(
                    calculator.calculateAnnualRewardPrecise(
                            rule, new BigDecimal("500")),
                    calculator.calculateAnnualRewardPrecise(
                            effective(rule), new BigDecimal("500"))
            );
        }
    }

    @Test
    void effectiveRuleKeepsCapFallbackAndValidation() {
        EffectiveRewardRule capped = new EffectiveRewardRule(
                "GROCERIES", new BigDecimal("4"), null,
                new BigDecimal("300"), "MONTHLY");

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateAnnualReward(
                        capped, new BigDecimal("500")));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateAnnualReward(
                        capped, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateAnnualReward(
                        (RewardRuleTerms) null, BigDecimal.ONE));
    }
}
