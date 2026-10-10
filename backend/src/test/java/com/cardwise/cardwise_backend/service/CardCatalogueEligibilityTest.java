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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardCatalogueEligibilityTest {

    private static final String REQUIREMENT =
            "CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT";

    @Mock
    private CardSelectionPolicyRepository policyRepository;

    @Mock
    private RewardRuleRepository rewardRuleRepository;

    private CardCatalogueEligibility realMode;

    @BeforeEach
    void setUp() {
        realMode = eligibility("REAL");
    }

    private CardCatalogueEligibility eligibility(String mode) {
        return new CardCatalogueEligibility(
                new CardCatalogueMode(mode),
                new SelectionPolicyViewService(
                        policyRepository, rewardRuleRepository));
    }

    private CreditCard card(boolean demo, boolean withheld) {
        CreditCard card = mock(CreditCard.class);
        lenient().when(card.getId()).thenReturn(5L);
        lenient().when(card.isDemo()).thenReturn(demo);
        lenient().when(card.isCatalogueWithheld()).thenReturn(withheld);
        return card;
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

    private void givenPolicy(String requirement, List<RewardRule> rules) {
        CardSelectionPolicy policy = new CardSelectionPolicy();
        policy.setCreditCardId(5L);
        policy.setBaseSelectionLimit(2);
        policy.setExtendedSelectionLimit(3);
        policy.setExtendedRequirement(requirement);
        lenient().when(policyRepository.findById(5L))
                .thenReturn(Optional.of(policy));
        lenient().when(rewardRuleRepository.findByCreditCardId(5L))
                .thenReturn(rules);
    }

    private List<RewardRule> validRules() {
        return List.of(
                rule("GROCERIES", true), rule("GAS", true),
                rule("DINING", true), rule("TRANSIT", true),
                rule("OTHER", false));
    }

    @Test
    void cardWithoutAPolicyIsEligible() {
        when(policyRepository.findById(5L)).thenReturn(Optional.empty());

        assertTrue(realMode.isEligible(card(false, false)));
    }

    @Test
    void cardWithAValidConfigurationIsEligible() {
        givenPolicy(REQUIREMENT, validRules());

        assertTrue(realMode.isEligible(card(false, false)));
    }

    @Test
    void cardWithMissingDisplayMetadataIsNotEligible() {
        givenPolicy("UNRECOGNISED_PROGRAM", validRules());
        assertFalse(realMode.isEligible(card(false, false)));

        givenPolicy(null, validRules());
        assertFalse(realMode.isEligible(card(false, false)));
    }

    @Test
    void cardWithAMissingCategoryLabelIsNotEligible() {
        givenPolicy(REQUIREMENT, List.of(
                rule("GROCERIES", true), rule("EV_CHARGING", true)));

        assertFalse(realMode.isEligible(card(false, false)));
    }

    @Test
    void withheldCardIsNotEligibleEvenWithAValidConfiguration() {
        givenPolicy(REQUIREMENT, validRules());

        assertFalse(realMode.isEligible(card(false, true)));
    }

    @Test
    void withheldAndModeChecksNeverTriggerPolicyLookups() {
        assertFalse(realMode.isEligible(card(false, true)));
        assertFalse(realMode.isEligible(card(true, false)));

        verifyNoInteractions(policyRepository, rewardRuleRepository);
    }

    @Test
    void demoCardIsEligibleOnlyInDemoMode() {
        when(policyRepository.findById(5L)).thenReturn(Optional.empty());

        assertTrue(eligibility("DEMO").isEligible(card(true, false)));
        assertFalse(eligibility("DEMO").isEligible(card(false, false)));
    }
}
