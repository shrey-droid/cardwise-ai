package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import com.cardwise.cardwise_backend.entity.RewardRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardRuleResolverTest {

    private static final BigDecimal TWO = new BigDecimal("2.0000");
    private static final BigDecimal HALF = new BigDecimal("0.5000");

    private RewardRuleResolver resolver;
    private CardSelectionPolicy policy;
    private List<RewardRule> rules;

    @BeforeEach
    void setUp() {
        resolver = new RewardRuleResolver();

        policy = new CardSelectionPolicy();
        policy.setCreditCardId(1L);
        policy.setBaseSelectionLimit(2);
        policy.setExtendedSelectionLimit(3);
        policy.setExtendedRequirement("CASHBACK_DEPOSITED_TO_SAVINGS");

        rules = List.of(
                selectable("GROCERIES"),
                selectable("GAS"),
                selectable("DINING"),
                selectable("TRANSIT"),
                fixed("TRAVEL"),
                fixed("OTHER")
        );
    }

    private RewardRule selectable(String category) {
        RewardRule rule = fixed(category);
        rule.setRewardRate(TWO);
        rule.setSelectable(true);
        rule.setUnselectedRewardRate(HALF);
        return rule;
    }

    private RewardRule fixed(String category) {
        RewardRule rule = new RewardRule();
        rule.setSpendingCategory(category);
        rule.setRewardRate(HALF);
        return rule;
    }

    private RewardSelection select(boolean confirmed, String... categories) {
        return new RewardSelection(Set.of(categories), confirmed);
    }

    private Map<String, EffectiveRewardRule> resolve(
            RewardSelection selection) {
        return resolver.resolve(rules, policy, selection);
    }

    @Test
    void selectedCategoriesGetTwoPercentAndOthersGetHalfPercent() {
        Map<String, EffectiveRewardRule> result =
                resolve(select(false, "GROCERIES", "GAS"));

        assertEquals(0, TWO.compareTo(result.get("GROCERIES").rewardRate()));
        assertEquals(0, TWO.compareTo(result.get("GAS").rewardRate()));
        assertEquals(0, HALF.compareTo(result.get("DINING").rewardRate()));
        assertEquals(0, HALF.compareTo(result.get("TRANSIT").rewardRate()));
        assertEquals(0, HALF.compareTo(result.get("TRAVEL").rewardRate()));
        assertEquals(0, HALF.compareTo(result.get("OTHER").rewardRate()));
    }

    @Test
    void noSelectionRatesEverySelectableCategoryAtBaseline() {
        Map<String, EffectiveRewardRule> result =
                resolve(RewardSelection.NONE);

        for (String category :
                List.of("GROCERIES", "GAS", "DINING", "TRANSIT")) {
            assertEquals(0, HALF.compareTo(result.get(category).rewardRate()));
        }
    }

    @Test
    void nullSelectionBehavesLikeNoSelection() {
        Map<String, EffectiveRewardRule> result = resolve(null);

        assertEquals(0, HALF.compareTo(result.get("GROCERIES").rewardRate()));
    }

    @Test
    void selectionIsCaseAndWhitespaceInsensitive() {
        Map<String, EffectiveRewardRule> result =
                resolve(new RewardSelection(Set.of(" groceries "), false));

        assertEquals(0, TWO.compareTo(result.get("GROCERIES").rewardRate()));
    }

    @Test
    void threeCategoriesAreRejectedWithoutConfirmedRequirement() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> resolve(select(false, "GROCERIES", "GAS", "DINING"))
        );

        assertTrue(error.getMessage().contains("CASHBACK_DEPOSITED_TO_SAVINGS"));
    }

    @Test
    void threeCategoriesAreAllowedWithConfirmedRequirement() {
        Map<String, EffectiveRewardRule> result =
                resolve(select(true, "GROCERIES", "GAS", "DINING"));

        assertEquals(0, TWO.compareTo(result.get("DINING").rewardRate()));
        assertEquals(0, HALF.compareTo(result.get("TRANSIT").rewardRate()));
    }

    @Test
    void fourCategoriesAreRejectedEvenWithConfirmedRequirement() {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolve(select(
                        true, "GROCERIES", "GAS", "DINING", "TRANSIT"))
        );
    }

    @Test
    void confirmedRequirementDoesNotRaiseTheTwoCategoryLimitForFewerPicks() {
        Map<String, EffectiveRewardRule> result =
                resolve(select(true, "GAS"));

        assertEquals(0, TWO.compareTo(result.get("GAS").rewardRate()));
        assertEquals(0, HALF.compareTo(result.get("GROCERIES").rewardRate()));
    }

    @Test
    void nonSelectableCategoryIsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> resolve(select(false, "TRAVEL"))
        );

        assertTrue(error.getMessage().contains("not a selectable"));
    }

    @Test
    void unknownCategoryIsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> resolve(select(false, "FURNITURE"))
        );

        assertTrue(error.getMessage().contains("Unknown"));
    }

    @Test
    void cardWithoutPolicyIgnoresSelectionAndKeepsRates() {
        RewardRule fixedRule = fixed("GROCERIES");
        fixedRule.setRewardRate(new BigDecimal("2.0000"));

        Map<String, EffectiveRewardRule> result = resolver.resolve(
                List.of(fixedRule), null, select(false, "GAS"));

        assertEquals(0, TWO.compareTo(result.get("GROCERIES").rewardRate()));
    }

    @Test
    void capsAndFallbackRatesArePreserved() {
        RewardRule capped = selectable("GROCERIES");
        capped.setSpendingCap(new BigDecimal("500.00"));
        capped.setCapPeriod("MONTHLY");
        capped.setBaseRewardRate(new BigDecimal("1.0000"));

        EffectiveRewardRule effective = resolver.resolve(
                List.of(capped), policy, select(false, "GROCERIES"))
                .get("GROCERIES");

        assertEquals(0, new BigDecimal("500.00")
                .compareTo(effective.getSpendingCap()));
        assertEquals("MONTHLY", effective.getCapPeriod());
        assertEquals(0, new BigDecimal("1.0000")
                .compareTo(effective.getBaseRewardRate()));
    }

    @Test
    void sourceEntitiesAreNeverModified() {
        resolve(RewardSelection.NONE);

        RewardRule groceries = rules.get(0);
        assertEquals(0, TWO.compareTo(groceries.getRewardRate()));
        assertTrue(groceries.isSelectable());
        assertFalse(rules.get(4).isSelectable());
    }

    @Test
    void resolvedRulesProduceExpectedRewardsInTheRealCalculator() {
        RewardCalculationService calculator = new RewardCalculationService();
        Map<String, EffectiveRewardRule> result =
                resolve(select(false, "GROCERIES"));

        // $600/month * 12 * 2% = $144.00
        assertEquals(new BigDecimal("144.00"), calculator.calculateAnnualReward(
                result.get("GROCERIES"), new BigDecimal("600")));
        // $600/month * 12 * 0.5% = $36.00
        assertEquals(new BigDecimal("36.00"), calculator.calculateAnnualReward(
                result.get("GAS"), new BigDecimal("600")));
    }
}
