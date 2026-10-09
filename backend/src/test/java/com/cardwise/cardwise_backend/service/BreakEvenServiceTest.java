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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BreakEvenServiceTest {

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private RewardRuleRepository rewardRuleRepository;

        private RewardCalculationService rewardCalculationService;

    private BreakEvenService breakEvenService;

    @BeforeEach
    void setUp() {
                rewardCalculationService = new RewardCalculationService();
        breakEvenService = new BreakEvenService(
                creditCardRepository,
                rewardRuleRepository,
                rewardCalculationService,
                new CardCatalogueMode("DEMO")
        );
    }

    private Map<String, BigDecimal> spending(String groceries) {
        return Map.of(
                "GROCERIES", new BigDecimal(groceries),
                "GAS", new BigDecimal("200"),
                "DINING", new BigDecimal("300"),
                "TRAVEL", new BigDecimal("100"),
                "OTHER", new BigDecimal("400")
        );
    }

    private CreditCard card(
            Long id,
            String name,
            String annualFee
    ) {
        CreditCard card = mock(CreditCard.class);

        lenient().when(card.getCardName()).thenReturn(name);
        lenient().when(card.getAnnualFee())
                .thenReturn(new BigDecimal(annualFee));
        lenient().when(card.isDemo()).thenReturn(true);

        return card;
    }

    private RewardRule rule(String category, String rate) {
        RewardRule rule = mock(RewardRule.class);

        when(rule.getSpendingCategory()).thenReturn(category);
        when(rule.getRewardRate())
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

    private void mockCards() {
        CreditCard everydayCard =
                card(1L, "Everyday Cashback", "0");
        CreditCard groceryPlusCard =
                card(2L, "Grocery Rewards Plus", "120");

        // Build rule lists before repository stubbing
        // to avoid nested Mockito stubbing.
        List<RewardRule> everydayRewardRules = everydayRules();
        List<RewardRule> groceryPlusRewardRules = groceryPlusRules();

        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(everydayCard));

        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(groceryPlusCard));

        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(everydayRewardRules);

        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(groceryPlusRewardRules);
    }

    private void mockCustomGroceryCards(
            String rateA,
            String feeA,
            String rateB,
            String feeB
    ) {
        CreditCard cardA = card(1L, "Card A", feeA);
        CreditCard cardB = card(2L, "Card B", feeB);

        List<RewardRule> rulesA = List.of(
                rule("GROCERIES", rateA),
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );

        List<RewardRule> rulesB = List.of(
                rule("GROCERIES", rateB),
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );

        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(cardA));

        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(cardB));

        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(rulesA);

        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(rulesB);
    }

    private void assertAmount(
            String expected,
            Object actual
    ) {
        assertNotNull(actual);
        assertEquals(
                0,
                new BigDecimal(expected)
                        .compareTo((BigDecimal) actual)
        );
    }

    @Test
    void shouldCalculateCorrectBreakEven() {
        mockCards();

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        2L, 1L, spending("100")
                );

        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertAmount("166.67", result.get("breakEvenMonthlyGroceries"));
        assertAmount("66.67", result.get("additionalMonthlyGroceries"));
        assertTrue(result.get("recommendation")
                .toString().contains("Grocery Rewards Plus"));
    }

    @Test
    void shouldHandleReversedCardSelection() {
        mockCards();

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, spending("100")
                );

        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertAmount("166.67", result.get("breakEvenMonthlyGroceries"));

        String recommendation = result.get("recommendation").toString();
        assertTrue(recommendation.contains("Everyday Cashback"));
        assertTrue(recommendation.contains("Grocery Rewards Plus"));
    }

    @Test
    void shouldHandleEqualGroceryRates() {
        CreditCard firstCard = card(1L, "First Card", "0");
        CreditCard secondCard = card(2L, "Second Card", "120");

        List<RewardRule> firstRules = everydayRules();
        List<RewardRule> secondRules = everydayRules();

        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(firstCard));

        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(secondCard));

        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(firstRules);

        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(secondRules);

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, spending("100")
                );

        assertEquals("NO_CROSSOVER", result.get("status"));
        assertNull(result.get("breakEvenMonthlyGroceries"));
        assertTrue(result.get("recommendation")
                .toString().contains("First Card"));
    }

    @Test
    void shouldHandleNegativeMathematicalCrossover() {
        CreditCard firstCard = card(1L, "High Cashback Card", "0");
        CreditCard secondCard = card(2L, "Low Cashback Card", "120");

        List<RewardRule> firstRules = groceryPlusRules();
        List<RewardRule> secondRules = everydayRules();

        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(firstCard));

        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(secondCard));

        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(firstRules);

        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(secondRules);

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, spending("100")
                );

        assertEquals(
                "NO_NONNEGATIVE_CROSSOVER",
                result.get("status")
        );
        assertNull(result.get("breakEvenMonthlyGroceries"));
        assertTrue(result.get("recommendation")
                .toString().contains("High Cashback Card"));
    }

    @Test
    void shouldFindMultipleCrossoversAcrossMonthlyGroceryCap() {
        CreditCard cappedCard =
                card(1L, "Capped Grocery Card", "40.00");
        CreditCard flatCard =
                card(2L, "Flat Cashback Card", "0.00");

        RewardRule cappedGroceries = rule("GROCERIES", "4.00");
        lenient().when(cappedGroceries.getSpendingCap())
                .thenReturn(new BigDecimal("500.00"));
        lenient().when(cappedGroceries.getCapPeriod())
                .thenReturn("MONTHLY");
        lenient().when(cappedGroceries.getBaseRewardRate())
                .thenReturn(new BigDecimal("1.00"));

        RewardRule flatGroceries = rule("GROCERIES", "2.00");
        List<RewardRule> cappedRules = List.of(
                cappedGroceries,
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );
        List<RewardRule> flatRules = List.of(
                flatGroceries,
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );

        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(cappedCard));
        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(flatCard));
        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(cappedRules);
        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(flatRules);

        Map<String, BigDecimal> cappedSpending = Map.of(
                "GROCERIES", new BigDecimal("800"),
                "GAS", BigDecimal.ZERO,
                "DINING", BigDecimal.ZERO,
                "TRAVEL", BigDecimal.ZERO,
                "OTHER", BigDecimal.ZERO
        );

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, cappedSpending
                );

        assertEquals("MULTIPLE_CROSSOVERS", result.get("status"));
        assertAmount(
                "166.67",
                result.get("breakEvenMonthlyGroceries")
        );

        @SuppressWarnings("unchecked")
        List<BigDecimal> breakEvenPoints =
                (List<BigDecimal>) result.get("breakEvenPoints");

        assertNotNull(breakEvenPoints);
        assertEquals(2, breakEvenPoints.size());
        assertAmount("166.67", breakEvenPoints.get(0));
        assertAmount("1166.67", breakEvenPoints.get(1));
        assertAmount(
                "366.67",
                result.get("additionalMonthlyGroceries")
        );
    }

    @Test
    void shouldDeduplicateCrossoverAtMonthlyCapBoundary() {
        CreditCard cappedCard =
                card(1L, "Capped Grocery Card", "120.00");
        CreditCard flatCard =
                card(2L, "Flat Cashback Card", "0.00");

        RewardRule cappedGroceries = rule("GROCERIES", "4.00");
        lenient().when(cappedGroceries.getSpendingCap())
                .thenReturn(new BigDecimal("500.00"));
        lenient().when(cappedGroceries.getCapPeriod())
                .thenReturn("MONTHLY");
        lenient().when(cappedGroceries.getBaseRewardRate())
                .thenReturn(new BigDecimal("1.00"));

        RewardRule flatGroceries = rule("GROCERIES", "2.00");
        List<RewardRule> cappedRules = List.of(
                cappedGroceries,
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );
        List<RewardRule> flatRules = List.of(
                flatGroceries,
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );

        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(cappedCard));
        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(flatCard));
        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(cappedRules);
        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(flatRules);

        Map<String, BigDecimal> cappedSpending = Map.of(
                "GROCERIES", new BigDecimal("400"),
                "GAS", BigDecimal.ZERO,
                "DINING", BigDecimal.ZERO,
                "TRAVEL", BigDecimal.ZERO,
                "OTHER", BigDecimal.ZERO
        );

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, cappedSpending
                );

        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertAmount("500.00", result.get("breakEvenMonthlyGroceries"));
        assertAmount("100.00", result.get("additionalMonthlyGroceries"));

        @SuppressWarnings("unchecked")
        List<BigDecimal> breakEvenPoints =
                (List<BigDecimal>) result.get("breakEvenPoints");

        assertEquals(1, breakEvenPoints.size());
        assertAmount("500.00", breakEvenPoints.get(0));
    }

    @Test
    void shouldHandleIdenticalRewardCurves() {
        mockCustomGroceryCards(
                "2.00", "0.00",
                "2.00", "0.00"
        );

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, spending("400")
                );

        assertEquals("NO_CROSSOVER", result.get("status"));
        assertNull(result.get("breakEvenMonthlyGroceries"));
        assertNull(result.get("additionalMonthlyGroceries"));

        @SuppressWarnings("unchecked")
        List<BigDecimal> points =
                (List<BigDecimal>) result.get("breakEvenPoints");

        assertNotNull(points);
        assertTrue(points.isEmpty());
        assertTrue(result.get("recommendation")
                .toString().contains("same net rewards"));
    }

    @Test
    void shouldDetectUnboundedTieIntervalAfterMonthlyCap() {
        CreditCard cappedCard =
                card(1L, "Capped Grocery Card", "120.00");
        CreditCard flatCard =
                card(2L, "Flat Cashback Card", "0.00");

        RewardRule cappedGroceries = rule("GROCERIES", "4.00");

        lenient().when(cappedGroceries.getSpendingCap())
                .thenReturn(new BigDecimal("500.00"));
        lenient().when(cappedGroceries.getCapPeriod())
                .thenReturn("MONTHLY");
        lenient().when(cappedGroceries.getBaseRewardRate())
                .thenReturn(new BigDecimal("2.00"));

        RewardRule flatGroceries = rule("GROCERIES", "2.00");

        List<RewardRule> cappedRules = List.of(
                cappedGroceries,
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );

        List<RewardRule> flatRules = List.of(
                flatGroceries,
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );

        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(cappedCard));
        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(flatCard));

        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(cappedRules);
        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(flatRules);

        Map<String, BigDecimal> monthlySpending = Map.of(
                "GROCERIES", new BigDecimal("400"),
                "GAS", BigDecimal.ZERO,
                "DINING", BigDecimal.ZERO,
                "TRAVEL", BigDecimal.ZERO,
                "OTHER", BigDecimal.ZERO
        );

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, monthlySpending
                );

        assertEquals("TIE_INTERVAL", result.get("status"));
        assertAmount(
                "500.00",
                result.get("breakEvenMonthlyGroceries")
        );
        assertAmount(
                "100.00",
                result.get("additionalMonthlyGroceries")
        );

        @SuppressWarnings("unchecked")
        List<BigDecimal> breakEvenPoints =
                (List<BigDecimal>) result.get("breakEvenPoints");

        assertNotNull(breakEvenPoints);
        assertTrue(
                breakEvenPoints.isEmpty(),
                "A tie interval must not be represented as isolated crossovers"
        );

        @SuppressWarnings("unchecked")
        List<Map<String, BigDecimal>> tieIntervals =
                (List<Map<String, BigDecimal>>) result.get("tieIntervals");

        assertNotNull(tieIntervals);
        assertEquals(1, tieIntervals.size());

        assertAmount("500.00", tieIntervals.get(0).get("start"));
        assertNull(tieIntervals.get(0).get("end"));
    }

    @Test
    void shouldMergeAdjacentTieIntervals() {
        CreditCard cardA = card(1L, "Card A", "0");
        CreditCard cardB = card(2L, "Card B", "0");

        RewardRule groceryA = rule("GROCERIES", "2");
        RewardRule groceryB = rule("GROCERIES", "2");

        lenient().when(groceryA.getSpendingCap())
                .thenReturn(new BigDecimal("500"));
        lenient().when(groceryA.getCapPeriod())
                .thenReturn("MONTHLY");
        lenient().when(groceryA.getBaseRewardRate())
                .thenReturn(new BigDecimal("2"));

        lenient().when(groceryB.getSpendingCap())
                .thenReturn(new BigDecimal("800"));
        lenient().when(groceryB.getCapPeriod())
                .thenReturn("MONTHLY");
        lenient().when(groceryB.getBaseRewardRate())
                .thenReturn(new BigDecimal("2"));

        List<RewardRule> rulesA = List.of(
                groceryA,
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );

        List<RewardRule> rulesB = List.of(
                groceryB,
                rule("GAS", "0"),
                rule("DINING", "0"),
                rule("TRAVEL", "0"),
                rule("OTHER", "0")
        );

        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(cardA));
        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(cardB));

        when(rewardRuleRepository.findByCreditCardId(1L))
                .thenReturn(rulesA);
        when(rewardRuleRepository.findByCreditCardId(2L))
                .thenReturn(rulesB);

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, spending("400")
                );

        @SuppressWarnings("unchecked")
        List<Map<String, BigDecimal>> intervals =
                (List<Map<String, BigDecimal>>) result.get("tieIntervals");

        assertNotNull(intervals);
        assertEquals(1, intervals.size());

        assertAmount("0", intervals.get(0).get("start"));
        assertNull(intervals.get(0).get("end"));
    }

    @Test
    void shouldCalculateFractionalCentBreakEvenPrecisely() {
        mockCustomGroceryCards(
                "3.00", "10.00",
                "2.00", "0.00"
        );

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, spending("50")
                );

        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertAmount(
                "83.33",
                result.get("breakEvenMonthlyGroceries")
        );
        assertAmount(
                "33.33",
                result.get("additionalMonthlyGroceries")
        );
    }

    @Test
    void shouldHandleVerySmallRewardRateDifference() {
        mockCustomGroceryCards(
                "2.0001", "0.01",
                "2.0000", "0.00"
        );

        Map<String, Object> result =
                breakEvenService.calculateBreakEven(
                        1L, 2L, spending("500")
                );

        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertAmount(
                "833.33",
                result.get("breakEvenMonthlyGroceries")
        );
        assertAmount(
                "333.33",
                result.get("additionalMonthlyGroceries")
        );
    }

    @Test
    void shouldRejectSameCardSelection() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> breakEvenService.calculateBreakEven(
                                1L, 1L, spending("100")
                        )
                );

        assertTrue(exception.getMessage()
                .contains("two different cards"));

        verifyNoInteractions(
                creditCardRepository,
                rewardRuleRepository
        );
    }

    @Test
    void shouldRejectDemoCardsInRealCatalogueMode() {
        CreditCard demoCardA = card(1L, "Demo A", "0");
        CreditCard demoCardB = card(2L, "Demo B", "0");
        when(creditCardRepository.findById(1L))
                .thenReturn(Optional.of(demoCardA));
        when(creditCardRepository.findById(2L))
                .thenReturn(Optional.of(demoCardB));

        breakEvenService = new BreakEvenService(
                creditCardRepository,
                rewardRuleRepository,
                rewardCalculationService,
                new CardCatalogueMode("REAL")
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> breakEvenService.calculateBreakEven(
                        1L,
                        2L,
                        spending("600")
                )
        );

        assertEquals("Card A not found.", exception.getMessage());
        verifyNoInteractions(rewardRuleRepository);
    }

    @Test
    void shouldRejectMissingCard() {
        when(creditCardRepository.findById(99L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> breakEvenService.calculateBreakEven(
                                99L, 1L, spending("100")
                        )
                );

        assertEquals("Card A not found.", exception.getMessage());
    }
}
