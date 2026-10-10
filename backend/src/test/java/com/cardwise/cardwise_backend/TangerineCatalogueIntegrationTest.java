package com.cardwise.cardwise_backend;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CardSelectionPolicyRepository;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;
import com.cardwise.cardwise_backend.service.RewardCalculationService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class TangerineCatalogueIntegrationTest {

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private RewardRuleRepository rewardRuleRepository;

    @Autowired
    private CardSelectionPolicyRepository policyRepository;

    private CreditCard card(String name) {
        return creditCardRepository.findAll().stream()
                .filter(card -> name.equals(card.getCardName()))
                .findFirst()
                .orElseThrow();
    }

    private List<RewardRule> tangerineRules() {
        return rewardRuleRepository
                .findByCreditCardId(card("Tangerine Money-Back Credit Card")
                        .getId());
    }

    @Test
    void tangerineIsARealZeroFeeCashbackCardWithVerificationMetadata() {
        CreditCard tangerine = card("Tangerine Money-Back Credit Card");

        assertFalse(tangerine.isDemo());
        assertEquals("Tangerine", tangerine.getIssuer());
        assertEquals("CASHBACK", tangerine.getRewardType());
        assertEquals(0, tangerine.getAnnualFee().compareTo(BigDecimal.ZERO));
        assertEquals(
                "https://www.tangerine.ca/en/personal/spend/credit-cards/money-back-credit-card",
                tangerine.getOfficialUrl());
        assertEquals(LocalDate.of(2026, 10, 10), tangerine.getLastVerifiedAt());
    }

    @Test
    void tangerineHasEightRulesWithFourSelectable() {
        List<RewardRule> rules = tangerineRules();

        assertEquals(8, rules.size());

        Set<String> selectable = rules.stream()
                .filter(RewardRule::isSelectable)
                .map(RewardRule::getSpendingCategory)
                .collect(Collectors.toSet());
        assertEquals(Set.of("GROCERIES", "GAS", "DINING", "TRANSIT"),
                selectable);

        for (RewardRule rule : rules) {
            if (rule.isSelectable()) {
                assertEquals(0, new BigDecimal("2.0000")
                        .compareTo(rule.getRewardRate()));
                assertEquals(0, new BigDecimal("0.5000")
                        .compareTo(rule.getUnselectedRewardRate()));
            } else {
                assertEquals(0, new BigDecimal("0.5000")
                        .compareTo(rule.getRewardRate()));
                assertNull(rule.getUnselectedRewardRate());
            }
            assertNull(rule.getSpendingCap());
        }
    }

    @Test
    void selectionPolicyLimitsAreStored() {
        CardSelectionPolicy policy = policyRepository
                .findById(card("Tangerine Money-Back Credit Card").getId())
                .orElseThrow();

        assertEquals(2, policy.getBaseSelectionLimit());
        assertEquals(3, policy.getExtendedSelectionLimit());
        assertEquals("CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT",
                policy.getExtendedRequirement());
        assertEquals(90, policy.getChangeHoldDays());
    }

    @Test
    void tangerineIsActiveInTheCatalogueAfterV12() {
        assertFalse(card("Tangerine Money-Back Credit Card")
                .isCatalogueWithheld());
        assertFalse(card("RBC Cash Back Mastercard").isCatalogueWithheld());
    }

    @Test
    void rbcRulesAreUnchangedAndNotSelectable() {
        CreditCard rbc = card("RBC Cash Back Mastercard");
        List<RewardRule> rules =
                rewardRuleRepository.findByCreditCardId(rbc.getId());

        assertEquals(8, rules.size());
        assertTrue(rules.stream().noneMatch(RewardRule::isSelectable));
        assertTrue(rules.stream()
                .allMatch(rule -> rule.getUnselectedRewardRate() == null));
        assertTrue(policyRepository.findById(rbc.getId()).isEmpty());

        Map<String, BigDecimal> spending = Map.of(
                "GROCERIES", new BigDecimal("600"),
                "GAS", new BigDecimal("200"),
                "DINING", new BigDecimal("300"),
                "TRAVEL", new BigDecimal("100"),
                "OTHER", new BigDecimal("400"));

        RewardCalculationService calculator = new RewardCalculationService();
        BigDecimal total = rules.stream()
                .filter(rule -> spending.containsKey(rule.getSpendingCategory()))
                .map(rule -> calculator.calculateAnnualReward(
                        rule, spending.get(rule.getSpendingCategory())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("216.00"), total);
    }
}
