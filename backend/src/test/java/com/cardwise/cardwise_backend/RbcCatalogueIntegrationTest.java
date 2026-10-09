package com.cardwise.cardwise_backend;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class RbcCatalogueIntegrationTest {

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private RewardRuleRepository rewardRuleRepository;

    @Test
    void rbcCardHasEightVerifiedRewardRules() {
        CreditCard rbc = creditCardRepository.findAll().stream()
                .filter(card -> !card.isDemo())
                .filter(card -> "RBC Cash Back Mastercard"
                        .equals(card.getCardName()))
                .findFirst()
                .orElseThrow();

        assertEquals("CASHBACK", rbc.getRewardType());
        assertEquals(0, rbc.getAnnualFee().compareTo(BigDecimal.ZERO));

        List<RewardRule> rules =
                rewardRuleRepository.findByCreditCardId(rbc.getId());

        assertEquals(8, rules.size());

        Map<String, BigDecimal> rates = rules.stream()
                .collect(Collectors.toMap(
                        rule -> rule.getSpendingCategory(),
                        rule -> rule.getRewardRate()
                ));

        assertEquals(Map.of(
                "GROCERIES", new BigDecimal("2.0000"),
                "GAS", new BigDecimal("1.0000"),
                "DINING", new BigDecimal("0.5000"),
                "TRAVEL", new BigDecimal("0.5000"),
                "OTHER", new BigDecimal("0.5000"),
                "TRANSIT", new BigDecimal("1.0000"),
                "RIDESHARE", new BigDecimal("1.0000"),
                "EV_CHARGING", new BigDecimal("1.0000")
        ), rates);
    }

    @Test
    void demoCardsRemainDemoOnly() {
        List<CreditCard> cards = creditCardRepository.findAll();

        assertTrue(cards.stream().anyMatch(card -> !card.isDemo()));
        assertTrue(cards.stream()
                .filter(card -> card.isDemo())
                .noneMatch(card ->
                        "RBC Cash Back Mastercard".equals(card.getCardName())));
    }
}
