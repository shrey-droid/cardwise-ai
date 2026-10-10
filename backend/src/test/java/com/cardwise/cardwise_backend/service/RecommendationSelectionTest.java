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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationSelectionTest {

    private static final long TANGERINE_ID = 20L;
    private static final long OTHER_SELECTABLE_ID = 21L;
    private static final long RBC_ID = 30L;

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private RewardRuleRepository rewardRuleRepository;

    @Mock
    private CardSelectionPolicyRepository policyRepository;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = serviceFor("REAL");
    }

    private RecommendationService serviceFor(String mode) {
        return new RecommendationService(
                creditCardRepository,
                rewardRuleRepository,
                new RewardCalculationService(),
                new CardCatalogueEligibility(
                        new CardCatalogueMode(mode),
                        new SelectionPolicyViewService(
                                policyRepository, rewardRuleRepository)),
                policyRepository,
                new RewardRuleResolver()
        );
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

    private CreditCard card(long id, String name, boolean withheld) {
        CreditCard card = mock(CreditCard.class);
        lenient().when(card.getId()).thenReturn(id);
        lenient().when(card.getCardName()).thenReturn(name);
        lenient().when(card.getRewardType()).thenReturn("CASHBACK");
        lenient().when(card.getAnnualFee()).thenReturn(BigDecimal.ZERO);
        lenient().when(card.isDemo()).thenReturn(false);
        lenient().when(card.isCatalogueWithheld()).thenReturn(withheld);
        return card;
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

    private CardSelectionPolicy policy(long cardId) {
        CardSelectionPolicy policy = new CardSelectionPolicy();
        policy.setCreditCardId(cardId);
        policy.setBaseSelectionLimit(2);
        policy.setExtendedSelectionLimit(3);
        policy.setExtendedRequirement(
                "CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT");
        return policy;
    }

    private void givenTangerine(boolean withheld) {
        CreditCard tangerine = card(TANGERINE_ID, "Tangerine", withheld);
        lenient().when(creditCardRepository.findAll())
                .thenReturn(List.of(tangerine));
        lenient().when(rewardRuleRepository.findByCreditCardId(TANGERINE_ID))
                .thenReturn(tangerineRules());
        lenient().when(policyRepository.findById(TANGERINE_ID))
                .thenReturn(Optional.of(policy(TANGERINE_ID)));
    }

    private RewardSelection selection(boolean confirmed, String... cats) {
        return new RewardSelection(Set.of(cats), confirmed);
    }

    private Map<String, Object> tangerineResult(RewardSelection selection) {
        return service.recommend(
                spending(), Map.of(TANGERINE_ID, selection)).get(0);
    }

    private void assertAnnual(String expected, Map<String, Object> result) {
        assertEquals(0, new BigDecimal(expected)
                .compareTo((BigDecimal) result.get("annualReward")));
    }

    @Test
    void noSelectionEarnsBaselineAndIsFlaggedIncomplete() {
        givenTangerine(false);

        Map<String, Object> result = tangerineResult(RewardSelection.NONE);

        // 1600/month * 12 * 0.5% = 96.00
        assertAnnual("96.00", result);
        assertEquals("NONE_SELECTED", result.get("selectionMode"));
        assertEquals(true, result.get("selectionRequired"));
        assertEquals(List.of(), result.get("selectedCategories"));
    }

    @Test
    void oneSelectionIsPartial() {
        givenTangerine(false);

        Map<String, Object> result =
                tangerineResult(selection(false, "GROCERIES"));

        // 144 + (200+300+100+400)*12*0.5% = 144 + 60
        assertAnnual("204.00", result);
        assertEquals("PARTIAL_SELECTION", result.get("selectionMode"));
        assertEquals(true, result.get("selectionRequired"));
    }

    @Test
    void groceriesAndGasEarnTwoPercentOnlyOnThoseCategories() {
        givenTangerine(false);

        Map<String, Object> result =
                tangerineResult(selection(false, "GROCERIES", "GAS"));

        // 144 + 48 + 300*12*0.5% + 100*12*0.5% + 400*12*0.5% = 240.00
        assertAnnual("240.00", result);
        assertEquals("SELECTED", result.get("selectionMode"));
        assertEquals(false, result.get("selectionRequired"));
        assertEquals(List.of("GAS", "GROCERIES"),
                result.get("selectedCategories"));

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> breakdown =
                (Map<String, BigDecimal>) result.get("rewardBreakdown");
        assertEquals(0, new BigDecimal("18.00")
                .compareTo(breakdown.get("DINING")));
    }

    @Test
    void threeSelectionsWithConfirmationAllEarnTwoPercent() {
        givenTangerine(false);

        Map<String, Object> result = tangerineResult(
                selection(true, "GROCERIES", "GAS", "DINING"));

        // 144 + 48 + 72 + 6 + 24 = 294.00
        assertAnnual("294.00", result);
        assertEquals("SELECTED", result.get("selectionMode"));
    }

    @Test
    void threeSelectionsWithoutConfirmationAreRejected() {
        givenTangerine(false);

        assertThrows(IllegalArgumentException.class, () -> tangerineResult(
                selection(false, "GROCERIES", "GAS", "DINING")));
    }

    @Test
    void fourSelectionsAreRejected() {
        givenTangerine(false);

        assertThrows(IllegalArgumentException.class, () -> tangerineResult(
                selection(true, "GROCERIES", "GAS", "DINING", "TRANSIT")));
    }

    @Test
    void invalidAndNonSelectableCategoriesAreRejected() {
        givenTangerine(false);

        assertThrows(IllegalArgumentException.class,
                () -> tangerineResult(selection(false, "FURNITURE")));
        assertThrows(IllegalArgumentException.class,
                () -> tangerineResult(selection(false, "TRAVEL")));
    }

    @Test
    void oneArgumentOverloadMatchesEmptySelections() {
        givenTangerine(false);

        assertEquals(
                service.recommend(spending()),
                service.recommend(spending(), Map.of())
        );
    }

    @Test
    void rbcIsUnchangedAndSelectionsForItAreIgnored() {
        CreditCard rbc = card(RBC_ID, "RBC", false);
        when(creditCardRepository.findAll()).thenReturn(List.of(rbc));
        when(rewardRuleRepository.findByCreditCardId(RBC_ID))
                .thenReturn(rbcRules());
        when(policyRepository.findById(RBC_ID)).thenReturn(Optional.empty());

        Map<String, Object> plain = service.recommend(spending()).get(0);
        Map<String, Object> withSelection = service.recommend(
                spending(), Map.of(RBC_ID, selection(false, "GAS"))).get(0);

        assertAnnual("216.00", plain);
        assertEquals(plain, withSelection);
        assertFalse(plain.containsKey("selectionMode"));
    }

    @Test
    void selectionForOneCardDoesNotApplyToAnother() {
        CreditCard first = card(TANGERINE_ID, "Selectable A", false);
        CreditCard second = card(OTHER_SELECTABLE_ID, "Selectable B", false);
        when(creditCardRepository.findAll())
                .thenReturn(List.of(first, second));
        when(rewardRuleRepository.findByCreditCardId(TANGERINE_ID))
                .thenReturn(tangerineRules());
        when(rewardRuleRepository.findByCreditCardId(OTHER_SELECTABLE_ID))
                .thenReturn(tangerineRules());
        when(policyRepository.findById(TANGERINE_ID))
                .thenReturn(Optional.of(policy(TANGERINE_ID)));
        when(policyRepository.findById(OTHER_SELECTABLE_ID))
                .thenReturn(Optional.of(policy(OTHER_SELECTABLE_ID)));

        List<Map<String, Object>> results = service.recommend(
                spending(),
                Map.of(TANGERINE_ID, selection(false, "GROCERIES", "GAS")));

        Map<String, Object> a = results.stream()
                .filter(r -> r.get("cardId").equals(TANGERINE_ID))
                .findFirst().orElseThrow();
        Map<String, Object> b = results.stream()
                .filter(r -> r.get("cardId").equals(OTHER_SELECTABLE_ID))
                .findFirst().orElseThrow();

        assertAnnual("240.00", a);
        assertAnnual("96.00", b);
        assertEquals("NONE_SELECTED", b.get("selectionMode"));
    }

    @Test
    void selectionsCannotBypassTheCatalogueFilterForAWithheldCard() {
        givenTangerine(true);

        List<Map<String, Object>> results = service.recommend(
                spending(),
                Map.of(TANGERINE_ID, selection(false, "GROCERIES", "GAS")));

        assertTrue(results.isEmpty());
        verify(rewardRuleRepository, never())
                .findByCreditCardId(TANGERINE_ID);
    }

    @Test
    void invalidSelectionForAWithheldCardIsNotEvenValidated() {
        givenTangerine(true);

        assertTrue(service.recommend(
                spending(), Map.of(TANGERINE_ID, selection(false, "TRAVEL")))
                .isEmpty());
    }
}
