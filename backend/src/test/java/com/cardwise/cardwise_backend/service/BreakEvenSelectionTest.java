package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CardSelectionPolicyRepository;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BreakEvenSelectionTest {

    private static final long RBC_ID = 1L;
    private static final long TANGERINE_ID = 2L;

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private RewardRuleRepository rewardRuleRepository;

    @Mock
    private CardSelectionPolicyRepository policyRepository;

    private BreakEvenService service;

    @BeforeEach
    void setUp() {
        service = new BreakEvenService(
                creditCardRepository,
                rewardRuleRepository,
                new RewardCalculationService(),
                new CardCatalogueEligibility(
                        new CardCatalogueMode("REAL"),
                        new SelectionPolicyViewService(
                                policyRepository, rewardRuleRepository)),
                policyRepository,
                new RewardRuleResolver()
        );

        givenCard(RBC_ID, "RBC", false, rbcRules());
        givenCard(TANGERINE_ID, "Tangerine", false, tangerineRules());
        lenient().when(policyRepository.findById(TANGERINE_ID))
                .thenReturn(Optional.of(policy()));
    }

    private void givenCard(
            long id, String name, boolean withheld, List<RewardRule> rules) {
        CreditCard card = mock(CreditCard.class);
        lenient().when(card.getCardName()).thenReturn(name);
        lenient().when(card.getAnnualFee()).thenReturn(BigDecimal.ZERO);
        lenient().when(card.isDemo()).thenReturn(false);
        lenient().when(card.isCatalogueWithheld()).thenReturn(withheld);
        lenient().when(creditCardRepository.findById(id))
                .thenReturn(Optional.of(card));
        lenient().when(rewardRuleRepository.findByCreditCardId(id))
                .thenReturn(rules);
    }

    private Map<String, BigDecimal> spending() {
        return Map.of(
                "GROCERIES", new BigDecimal("600"),
                "GAS", new BigDecimal("200"),
                "DINING", new BigDecimal("300"),
                "TRAVEL", new BigDecimal("100"),
                "OTHER", new BigDecimal("400"),
                "TRANSIT", BigDecimal.ZERO,
                "RIDESHARE", BigDecimal.ZERO,
                "EV_CHARGING", BigDecimal.ZERO
        );
    }

    private RewardRule rule(
            String category, String rate, boolean selectable,
            String unselectedRate) {
        RewardRule rule = new RewardRule();
        rule.setSpendingCategory(category);
        rule.setRewardRate(new BigDecimal(rate));
        rule.setSelectable(selectable);
        if (unselectedRate != null) {
            rule.setUnselectedRewardRate(new BigDecimal(unselectedRate));
        }
        return rule;
    }

    private List<RewardRule> tangerineRules() {
        return List.of(
                rule("GROCERIES", "2", true, "0.5"),
                rule("GAS", "2", true, "0.5"),
                rule("DINING", "2", true, "0.5"),
                rule("TRANSIT", "2", true, "0.5"),
                rule("TRAVEL", "0.5", false, null),
                rule("RIDESHARE", "0.5", false, null),
                rule("EV_CHARGING", "0.5", false, null),
                rule("OTHER", "0.5", false, null)
        );
    }

    private List<RewardRule> rbcRules() {
        return List.of(
                rule("GROCERIES", "2", false, null),
                rule("GAS", "1", false, null),
                rule("TRANSIT", "1", false, null),
                rule("RIDESHARE", "1", false, null),
                rule("EV_CHARGING", "1", false, null),
                rule("DINING", "0.5", false, null),
                rule("TRAVEL", "0.5", false, null),
                rule("OTHER", "0.5", false, null)
        );
    }

    private CardSelectionPolicy policy() {
        CardSelectionPolicy policy = new CardSelectionPolicy();
        policy.setCreditCardId(TANGERINE_ID);
        policy.setBaseSelectionLimit(2);
        policy.setExtendedSelectionLimit(3);
        policy.setExtendedRequirement(
                "CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT");
        return policy;
    }

    private Map<Long, RewardSelection> tangerine(
            boolean confirmed, String... categories) {
        return Map.of(
                TANGERINE_ID,
                new RewardSelection(Set.of(categories), confirmed));
    }

    private Map<String, Object> breakEven(
            Map<Long, RewardSelection> selections) {
        return service.calculateBreakEven(
                RBC_ID, TANGERINE_ID, spending(), selections);
    }

    @Test
    void groceryNotSelectedMakesRbcOvertakeTangerineAtTheCrossover() {
        // Tangerine fixed 150, RBC fixed 72; RBC gains 0.18 per grocery
        // dollar per month, so they cross at 78 / 0.18 = 433.33.
        Map<String, Object> result = breakEven(tangerine(false, "GAS", "DINING"));

        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertEquals(0, new BigDecimal("433.33").compareTo(
                (BigDecimal) result.get("breakEvenMonthlyGroceries")));
    }

    @Test
    void groceryEarningHalfPercentWithNoCrossoverKeepsExistingBehavior() {
        // Nothing selected: every Tangerine category earns 0.5%, so RBC
        // leads at $0 and its steeper grocery rate keeps it ahead.
        Map<String, Object> result = breakEven(Map.of());

        assertEquals("NO_NONNEGATIVE_CROSSOVER", result.get("status"));
        assertEquals(null, result.get("breakEvenMonthlyGroceries"));
        assertTrue(result.get("recommendation").toString().startsWith("RBC"));
    }

    @Test
    void groceryEarningHalfPercentInATangerineLeadIsStillHandled() {
        // Groceries unselected; Tangerine leads by 24 at $0 and RBC's 2%
        // grocery rate erases it at 24 / 0.18 = 133.33.
        Map<String, Object> result = breakEven(tangerine(false, "GAS"));

        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertEquals(0, new BigDecimal("133.33").compareTo(
                (BigDecimal) result.get("breakEvenMonthlyGroceries")));
    }

    @Test
    void selectedGroceriesGiveEqualSlopesAndNoCrossover() {
        Map<String, Object> result =
                breakEven(tangerine(false, "GROCERIES", "GAS"));

        assertEquals("NO_CROSSOVER", result.get("status"));
        assertTrue(result.get("recommendation").toString()
                .startsWith("Tangerine"));
    }

    @Test
    void resultIsIndependentOfCardOrder() {
        Map<String, Object> reversed = service.calculateBreakEven(
                TANGERINE_ID, RBC_ID, spending(),
                tangerine(false, "GAS", "DINING"));

        assertEquals(0, new BigDecimal("433.33").compareTo(
                (BigDecimal) reversed.get("breakEvenMonthlyGroceries")));
    }

    @Test
    void legacyOverloadMatchesEmptySelections() {
        assertEquals(
                service.calculateBreakEven(
                        RBC_ID, TANGERINE_ID, spending()),
                breakEven(Map.of())
        );
    }

    @Test
    void threeCategoriesRequireConfirmation() {
        assertThrows(IllegalArgumentException.class, () -> breakEven(
                tangerine(false, "GROCERIES", "GAS", "DINING")));

        Map<String, Object> result = breakEven(
                tangerine(true, "GROCERIES", "GAS", "DINING"));
        assertEquals("NO_CROSSOVER", result.get("status"));
    }

    @Test
    void invalidSelectionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> breakEven(tangerine(false, "FURNITURE")));
        assertThrows(IllegalArgumentException.class,
                () -> breakEven(tangerine(false, "TRAVEL")));
        assertThrows(IllegalArgumentException.class, () -> breakEven(
                tangerine(true, "GROCERIES", "GAS", "DINING", "TRANSIT")));
    }

    @Test
    void rbcIgnoresSelectionsAndIsUnchanged() {
        Map<Long, RewardSelection> onlyRbc = Map.of(
                RBC_ID, new RewardSelection(Set.of("GAS"), false));

        assertEquals(breakEven(Map.of()), breakEven(onlyRbc));
    }

    @Test
    void withheldCardIsNotFoundAndRulesAreNeverLoaded() {
        givenCard(TANGERINE_ID, "Tangerine", true, tangerineRules());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> breakEven(tangerine(false, "GAS", "DINING")));

        assertEquals("Card B not found.", error.getMessage());
        verify(rewardRuleRepository, never())
                .findByCreditCardId(TANGERINE_ID);
    }

    @Test
    void unknownCardGivesTheSameErrorAsAWithheldCard() {
        givenCard(TANGERINE_ID, "Tangerine", true, tangerineRules());
        lenient().when(creditCardRepository.findById(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException withheld = assertThrows(
                IllegalArgumentException.class,
                () -> breakEven(tangerine(false, "GAS")));
        IllegalArgumentException unknown = assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateBreakEven(
                        RBC_ID, 999L, spending(),
                        Map.of(999L, new RewardSelection(Set.of("GAS"), false))));

        assertEquals(withheld.getMessage(), unknown.getMessage());
    }
}
