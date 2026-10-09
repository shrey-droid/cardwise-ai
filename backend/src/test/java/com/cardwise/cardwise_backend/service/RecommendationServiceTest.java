package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private RewardRuleRepository rewardRuleRepository;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        RewardCalculationService rewardCalculationService =
                new RewardCalculationService();

        recommendationService = new RecommendationService(
                creditCardRepository,
                rewardRuleRepository,
                rewardCalculationService
        );
    }

    private Map<String, BigDecimal> spending() {
        return Map.of(
                "GROCERIES", new BigDecimal("600"),
                "GAS", new BigDecimal("200"),
                "DINING", new BigDecimal("300"),
                "TRAVEL", new BigDecimal("100"),
                "OTHER", new BigDecimal("400")
        );
    }

    private CreditCard card(
            Long id,
            String name,
            String rewardType,
            String annualFee) {

        CreditCard card = mock(CreditCard.class);

        lenient().when(card.getId()).thenReturn(id);
        lenient().when(card.getCardName()).thenReturn(name);
        when(card.getRewardType()).thenReturn(rewardType);
        lenient().when(card.getAnnualFee())
                .thenReturn(new BigDecimal(annualFee));

        return card;
    }

    private RewardRule rule(String category, String rate) {
        RewardRule rule = mock(RewardRule.class);

        when(rule.getSpendingCategory()).thenReturn(category);
        lenient().when(rule.getRewardRate())
                .thenReturn(new BigDecimal(rate));

        return rule;
    }

    private List<RewardRule> everydayRules() {
        return List.of(
                rule("GROCERIES", "1"),
                rule("GAS", "1"),
                rule("DINING", "1"),
                rule("TRAVEL", "1"),
                rule("OTHER", "1")
        );
    }

    private List<RewardRule> groceryPlusRules() {
        return List.of(
                rule("GROCERIES", "4"),
                rule("GAS", "2"),
                rule("DINING", "2"),
                rule("TRAVEL", "1"),
                rule("OTHER", "1")
        );
    }

    private void mockCashbackCards() {
        CreditCard everyday =
                card(1L, "Everyday Cashback", "CASHBACK", "0");

        CreditCard groceryPlus =
                card(2L, "Grocery Rewards Plus", "CASHBACK", "120");
        List<RewardRule> everydayRewardRules = everydayRules();
        List<RewardRule> groceryPlusRewardRules = groceryPlusRules();

        when(creditCardRepository.findAll())
                .thenReturn(List.of(everyday, groceryPlus));

        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(everydayRewardRules);

        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(groceryPlusRewardRules);
    }

    @Test
    void shouldCalculateAnnualCashbackCorrectly() {
        mockCashbackCards();

        List<Map<String, Object>> results =
                recommendationService.recommend(spending());

        Map<String, Object> groceryPlus = results.stream()
                .filter(result -> result.get("cardId").equals(2L))
                .findFirst()
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("468.00").compareTo(
                        (BigDecimal) groceryPlus.get("annualReward")
                )
        );

        assertEquals(
                0,
                new BigDecimal("348.00").compareTo(
                        (BigDecimal) groceryPlus.get("netAnnualReward")
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> breakdown =
                (Map<String, BigDecimal>) groceryPlus.get("rewardBreakdown");

        assertEquals(
                0,
                new BigDecimal("288.00").compareTo(
                        breakdown.get("GROCERIES")
                )
        );
    }

    @Test
    void shouldRankHighestNetRewardFirst() {
        mockCashbackCards();

        List<Map<String, Object>> results =
                recommendationService.recommend(spending());

        assertEquals(2, results.size());
        assertEquals(2L, results.get(0).get("cardId"));
        assertEquals(1L, results.get(1).get("cardId"));

        assertEquals(
                0,
                new BigDecimal("192.00").compareTo(
                        (BigDecimal) results.get(1).get("netAnnualReward")
                )
        );
    }

    @Test
    void shouldExcludePointsCards() {
        CreditCard pointsCard =
                card(3L, "Travel Points Explorer", "POINTS", "0");

        when(creditCardRepository.findAll())
                .thenReturn(List.of(pointsCard));

        List<Map<String, Object>> results =
                recommendationService.recommend(spending());

        assertTrue(results.isEmpty());

        verifyNoInteractions(rewardRuleRepository);
    }

    @Test
    void shouldExcludeCardsWithIncompleteRewardRules() {
        CreditCard everyday =
                card(1L, "Everyday Cashback", "CASHBACK", "0");
        List<RewardRule> incompleteRules = List.of(
                rule("GROCERIES", "1"),
                rule("GAS", "1")
        );

        when(creditCardRepository.findAll())
                .thenReturn(List.of(everyday));

        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(incompleteRules);

        List<Map<String, Object>> results =
                recommendationService.recommend(spending());

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldRejectNegativeSpending() {
        Map<String, BigDecimal> invalidSpending = Map.of(
                "GROCERIES", new BigDecimal("-100"),
                "GAS", BigDecimal.ZERO,
                "DINING", BigDecimal.ZERO,
                "TRAVEL", BigDecimal.ZERO,
                "OTHER", BigDecimal.ZERO
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> recommendationService.recommend(
                                invalidSpending
                        )
                );

        assertTrue(exception.getMessage().contains("GROCERIES"));

        verifyNoInteractions(creditCardRepository);
    }

    @Test
    void shouldRejectMissingSpendingCategory() {
        Map<String, BigDecimal> incompleteSpending = Map.of(
                "GROCERIES", BigDecimal.ZERO,
                "GAS", BigDecimal.ZERO,
                "DINING", BigDecimal.ZERO,
                "TRAVEL", BigDecimal.ZERO
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> recommendationService.recommend(
                        incompleteSpending
                )
        );

        verifyNoInteractions(creditCardRepository);
    }

    @Test
    void shouldApplyMonthlyGrocerySpendingCapAndBaseRateAboveCap() {
        CreditCard cappedCard =
                card(4L, "Capped Grocery Cashback", "CASHBACK", "0");

        RewardRule groceryRule = rule("GROCERIES", "4");
        lenient().when(groceryRule.getSpendingCap())
                .thenReturn(new BigDecimal("500"));
        lenient().when(groceryRule.getCapPeriod())
                .thenReturn("MONTHLY");
        lenient().when(groceryRule.getBaseRewardRate())
                .thenReturn(new BigDecimal("1.00"));

        List<RewardRule> cappedRules = List.of(
                groceryRule,
                rule("GAS", "1"),
                rule("DINING", "1"),
                rule("TRAVEL", "1"),
                rule("OTHER", "1")
        );

        Map<String, BigDecimal> cappedSpending = Map.of(
                "GROCERIES", new BigDecimal("800"),
                "GAS", BigDecimal.ZERO,
                "DINING", BigDecimal.ZERO,
                "TRAVEL", BigDecimal.ZERO,
                "OTHER", BigDecimal.ZERO
        );

        when(creditCardRepository.findAll())
                .thenReturn(List.of(cappedCard));
        when(rewardRuleRepository.findByCreditCardId(4L))
                .thenReturn(cappedRules);

        List<Map<String, Object>> results =
                recommendationService.recommend(cappedSpending);

        assertEquals(1, results.size());

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> breakdown =
                (Map<String, BigDecimal>) results.get(0)
                        .get("rewardBreakdown");

        // $500 at 4% plus $300 at the 1% base rate,
        // annualized: ($20 + $3) * 12 = $276.
        assertEquals(
                0,
                new BigDecimal("276.00").compareTo(
                        breakdown.get("GROCERIES")
                )
        );
    }

    @Test
    void shouldRoundEachCategoryBeforeCalculatingAnnualTotal() {
        CreditCard cashbackCard =
                card(10L, "Precision Cashback", "CASHBACK", "0");

        List<RewardRule> rules = List.of(
                rule("GROCERIES", "1"),
                rule("GAS", "1"),
                rule("DINING", "1"),
                rule("TRAVEL", "1"),
                rule("OTHER", "1")
        );

        when(creditCardRepository.findAll())
                .thenReturn(List.of(cashbackCard));

        when(rewardRuleRepository.findByCreditCardId(10L))
                .thenReturn(rules);

        Map<String, BigDecimal> monthlySpending = Map.of(
                "GROCERIES", new BigDecimal("0.0416666667"),
                "GAS", new BigDecimal("0.0416666667"),
                "DINING", new BigDecimal("0.0416666667"),
                "TRAVEL", BigDecimal.ZERO,
                "OTHER", BigDecimal.ZERO
        );

        List<Map<String, Object>> results =
                recommendationService.recommend(monthlySpending);

        assertEquals(1, results.size());

        Map<String, Object> result = results.get(0);

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> breakdown =
                (Map<String, BigDecimal>) result.get("rewardBreakdown");

        assertEquals(
                0,
                new BigDecimal("0.01")
                        .compareTo(breakdown.get("GROCERIES"))
        );

        assertEquals(
                0,
                new BigDecimal("0.01")
                        .compareTo(breakdown.get("GAS"))
        );

        assertEquals(
                0,
                new BigDecimal("0.01")
                        .compareTo(breakdown.get("DINING"))
        );

        assertEquals(
                0,
                new BigDecimal("0.03")
                        .compareTo(
                                (BigDecimal) result.get("annualReward")
                        )
        );
    }
}
