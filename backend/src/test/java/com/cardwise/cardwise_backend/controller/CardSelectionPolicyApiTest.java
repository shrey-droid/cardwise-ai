package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test rolls back, so un-withholding Tangerine never leaks out of a test.
@SpringBootTest(properties = "cardwise.catalogue.mode=REAL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CardSelectionPolicyApiTest {

    private static final String TANGERINE = "Tangerine Money-Back Credit Card";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private RewardRuleRepository rewardRuleRepository;

    private CreditCard card(String name) {
        return creditCardRepository.findAll().stream()
                .filter(card -> name.equals(card.getCardName()))
                .findFirst()
                .orElseThrow();
    }

    private void releaseTangerineForThisTest() {
        CreditCard tangerine = card(TANGERINE);
        tangerine.setCatalogueWithheld(false);
        creditCardRepository.saveAndFlush(tangerine);
    }

    // V12 activates Tangerine, so withheld behaviour needs its own fixture.
    private void withholdTangerineForThisTest() {
        CreditCard tangerine = card(TANGERINE);
        tangerine.setCatalogueWithheld(true);
        creditCardRepository.saveAndFlush(tangerine);
    }

    private String cardsBody(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/cards" + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> cardJson(String body, String name) {
        List<Map<String, Object>> found = JsonPath.read(
                body, "$[?(@.cardName=='" + name + "')]");
        return found.get(0);
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    @Test
    void rbcResponseKeepsItsOriginalFieldsAndHasNoPolicy() throws Exception {
        withholdTangerineForThisTest();

        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardName")
                        .value("RBC Cash Back Mastercard"))
                .andExpect(jsonPath("$[0].selectionPolicy").doesNotExist())
                .andExpect(jsonPath("$[0].catalogueWithheld").doesNotExist())
                // id, cardName, issuer, rewardType, annualFee, demo, two
                // income minimums, officialUrl, lastVerifiedAt, createdAt.
                .andExpect(jsonPath("$[0]").value(aMapWithSize(11)));
    }

    @Test
    void selectableCardIncludesPolicyLimitsAndFourCategories()
            throws Exception {
        releaseTangerineForThisTest();

        Map<String, Object> tangerine =
                cardJson(cardsBody(""), TANGERINE);
        assertEquals(card(TANGERINE).getId().intValue(), tangerine.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> policy =
                (Map<String, Object>) tangerine.get("selectionPolicy");

        assertEquals(2, policy.get("baseSelectionLimit"));
        assertEquals(3, policy.get("extendedSelectionLimit"));
        assertEquals("CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT",
                policy.get("extendedRequirement"));
        assertEquals(
                "My cashback is deposited into an eligible Tangerine "
                        + "Savings Account",
                policy.get("extendedRequirementLabel"));
        assertEquals(90, policy.get("changeHoldDays"));
        assertEquals(4, policy.get("modelledCategoryCount"));
        assertEquals(13, policy.get("offeredCategoryCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories =
                (List<Map<String, Object>>) policy.get("selectableCategories");

        assertEquals(
                List.of("GROCERIES", "GAS", "DINING", "TRANSIT"),
                categories.stream().map(c -> c.get("code")).toList());
        assertEquals(
                List.of("Groceries", "Gas", "Restaurants",
                        "Public Transportation and Parking"),
                categories.stream().map(c -> c.get("label")).toList());
    }

    @Test
    void policyRatesMatchTheDatabaseRules() throws Exception {
        releaseTangerineForThisTest();

        Map<String, RewardRule> rules = rewardRuleRepository
                .findByCreditCardId(card(TANGERINE).getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        RewardRule::getSpendingCategory, rule -> rule));

        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>)
                cardJson(cardsBody(""), TANGERINE).get("selectionPolicy");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories =
                (List<Map<String, Object>>) policy.get("selectableCategories");

        for (Map<String, Object> category : categories) {
            RewardRule rule = rules.get((String) category.get("code"));
            assertEquals(0, rule.getRewardRate()
                    .compareTo(decimal(category.get("selectedRate"))));
            assertEquals(0, rule.getUnselectedRewardRate()
                    .compareTo(decimal(category.get("unselectedRate"))));
        }
    }

    @Test
    void rbcStaysUnchangedWhenTangerineIsAlsoVisible() throws Exception {
        releaseTangerineForThisTest();

        Map<String, Object> rbc =
                cardJson(cardsBody(""), "RBC Cash Back Mastercard");

        assertFalse(rbc.containsKey("selectionPolicy"));
        assertFalse(rbc.containsKey("catalogueWithheld"));
        assertEquals(11, rbc.size());
    }

    @Test
    void withheldTangerineAndItsPolicyAreAbsentFromEveryCardsResponse()
            throws Exception {
        withholdTangerineForThisTest();

        for (String query : new String[] {"", "?rewardType=CASHBACK",
                "?rewardType=cashback", "?rewardType=POINTS"}) {
            String body = cardsBody(query);

            assertFalse(body.contains("Tangerine"));
            assertFalse(body.contains("selectionPolicy"));
            assertFalse(body.contains("CASHBACK_DEPOSITED"));
        }
    }

    @Test
    void unknownRewardTypeReturnsNoCardsAndNoPolicy() throws Exception {
        releaseTangerineForThisTest();

        assertEquals("[]", cardsBody("?rewardType=NO_SUCH_TYPE"));
    }
}
