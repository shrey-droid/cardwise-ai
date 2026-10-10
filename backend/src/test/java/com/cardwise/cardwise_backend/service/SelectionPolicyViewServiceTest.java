package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CardSelectionPolicyRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectionPolicyViewServiceTest {

    private static final String REQUIREMENT =
            "CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT";

    @Mock
    private CardSelectionPolicyRepository policyRepository;

    @Mock
    private RewardRuleRepository rewardRuleRepository;

    private SelectionPolicyViewService service;
    private CreditCard card;

    @BeforeEach
    void setUp() {
        service = new SelectionPolicyViewService(
                policyRepository, rewardRuleRepository);
        card = mock(CreditCard.class);
        when(card.getId()).thenReturn(5L);
    }

    private RewardRule rule(String category, boolean selectable) {
        RewardRule rule = new RewardRule();
        rule.setSpendingCategory(category);
        rule.setRewardRate(new BigDecimal(selectable ? "2" : "0.5"));
        rule.setSelectable(selectable);
        if (selectable) {
            rule.setUnselectedRewardRate(new BigDecimal("0.5"));
        }
        return rule;
    }

    private CardSelectionPolicy policy(String requirement) {
        CardSelectionPolicy policy = new CardSelectionPolicy();
        policy.setCreditCardId(5L);
        policy.setBaseSelectionLimit(2);
        policy.setExtendedSelectionLimit(3);
        policy.setExtendedRequirement(requirement);
        return policy;
    }

    @Test
    void cardWithoutPolicyHasNoView() {
        when(policyRepository.findById(5L)).thenReturn(Optional.empty());

        assertTrue(service.forCard(card).isEmpty());
    }

    @Test
    void categoriesAreOrderedAndOnlySelectableRulesAreIncluded() {
        when(policyRepository.findById(5L))
                .thenReturn(Optional.of(policy(REQUIREMENT)));
        when(rewardRuleRepository.findByCreditCardId(5L)).thenReturn(List.of(
                rule("OTHER", false),
                rule("TRANSIT", true),
                rule("DINING", true),
                rule("GAS", true),
                rule("GROCERIES", true)
        ));

        SelectionPolicyView view = service.forCard(card).orElseThrow();

        assertEquals(
                List.of("GROCERIES", "GAS", "DINING", "TRANSIT"),
                view.selectableCategories().stream()
                        .map(SelectionPolicyView.SelectableCategoryView::code)
                        .toList());
        assertEquals(4, view.modelledCategoryCount());
    }

    @Test
    void unrecognisedRequirementCodeOmitsThePolicy() {
        when(policyRepository.findById(5L))
                .thenReturn(Optional.of(policy("SOME_OTHER_PROGRAM")));
        when(rewardRuleRepository.findByCreditCardId(5L))
                .thenReturn(List.of(rule("GROCERIES", true)));

        assertTrue(service.forCard(card).isEmpty());
    }

    @Test
    void policyWithoutRequirementCodeOmitsThePolicy() {
        when(policyRepository.findById(5L))
                .thenReturn(Optional.of(policy(null)));
        when(rewardRuleRepository.findByCreditCardId(5L))
                .thenReturn(List.of(rule("GROCERIES", true)));

        assertTrue(service.forCard(card).isEmpty());
    }

    @Test
    void categoryWithoutALabelOmitsThePolicy() {
        when(policyRepository.findById(5L))
                .thenReturn(Optional.of(policy(REQUIREMENT)));
        when(rewardRuleRepository.findByCreditCardId(5L))
                .thenReturn(List.of(rule("GROCERIES", true),
                        rule("EV_CHARGING", true)));

        assertTrue(service.forCard(card).isEmpty());
    }

    private SelectionPolicyViewService.Status statusFor(
            List<RewardRule> rules) {
        when(policyRepository.findById(5L))
                .thenReturn(Optional.of(policy(REQUIREMENT)));
        when(rewardRuleRepository.findByCreditCardId(5L)).thenReturn(rules);

        return service.evaluate(card).status();
    }

    @Test
    void evaluateReportsNoPolicyForCardsWithoutOne() {
        when(policyRepository.findById(5L)).thenReturn(Optional.empty());

        assertEquals(SelectionPolicyViewService.Status.NO_POLICY,
                service.evaluate(card).status());
    }

    @Test
    void evaluateReportsValidForACompleteConfiguration() {
        assertEquals(SelectionPolicyViewService.Status.VALID, statusFor(List.of(
                rule("GROCERIES", true), rule("GAS", true),
                rule("DINING", true), rule("TRANSIT", true),
                rule("OTHER", false))));
    }

    @Test
    void policyWithNoSelectableRulesIsInvalid() {
        assertEquals(SelectionPolicyViewService.Status.INVALID,
                statusFor(List.of(rule("GROCERIES", false))));
    }

    @Test
    void baseLimitAboveTheSelectableCategoryCountIsInvalid() {
        // Base limit is 2 but only one category can be chosen.
        assertEquals(SelectionPolicyViewService.Status.INVALID,
                statusFor(List.of(rule("GROCERIES", true))));
    }

    @Test
    void missingOrInconsistentRatesAreInvalid() {
        RewardRule noUnselectedRate = rule("GAS", true);
        noUnselectedRate.setUnselectedRewardRate(null);
        assertEquals(SelectionPolicyViewService.Status.INVALID,
                statusFor(List.of(rule("GROCERIES", true), noUnselectedRate)));

        RewardRule selectedBelowUnselected = rule("GAS", true);
        selectedBelowUnselected.setRewardRate(new BigDecimal("0.25"));
        assertEquals(SelectionPolicyViewService.Status.INVALID,
                statusFor(List.of(
                        rule("GROCERIES", true), selectedBelowUnselected)));
    }

    @Test
    void anUnlabelledSelectableCategoryIsInvalid() {
        assertEquals(SelectionPolicyViewService.Status.INVALID,
                statusFor(List.of(rule("GROCERIES", true),
                        rule("EV_CHARGING", true))));
    }
}
