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

    private BreakEvenService breakEvenService;

    @BeforeEach
    void setUp() {
        breakEvenService = new BreakEvenService(
                creditCardRepository,
                rewardRuleRepository
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
