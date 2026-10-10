package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CardSelectionPolicyRepository;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test rolls back, so catalogue changes never leak out of a test.
@SpringBootTest(properties = "cardwise.catalogue.mode=REAL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogueEligibilityApiTest {

    private static final String TANGERINE = "Tangerine Money-Back Credit Card";
    private static final String RBC = "RBC Cash Back Mastercard";
    private static final String SPENDING =
            "groceries=600&gas=200&dining=300&travel=100&other=400";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreditCardRepository creditCardRepository;

    @Autowired
    private CardSelectionPolicyRepository policyRepository;

    @Autowired
    private RewardRuleRepository rewardRuleRepository;

    private CreditCard card(String name) {
        return creditCardRepository.findAll().stream()
                .filter(card -> name.equals(card.getCardName()))
                .findFirst()
                .orElseThrow();
    }

    private void release() {
        CreditCard tangerine = card(TANGERINE);
        tangerine.setCatalogueWithheld(false);
        creditCardRepository.saveAndFlush(tangerine);
    }

    private void breakRequirementCode() {
        CardSelectionPolicy policy =
                policyRepository.findById(card(TANGERINE).getId())
                        .orElseThrow();
        policy.setExtendedRequirement("UNRECOGNISED_PROGRAM");
        policyRepository.saveAndFlush(policy);
    }

    // Makes a category selectable that has no display label.
    private void addUnlabelledSelectableCategory() {
        RewardRule rule = rewardRuleRepository
                .findByCreditCardId(card(TANGERINE).getId()).stream()
                .filter(r -> "EV_CHARGING".equals(r.getSpendingCategory()))
                .findFirst().orElseThrow();
        rule.setSelectable(true);
        rule.setUnselectedRewardRate(new BigDecimal("0.5"));
        rewardRuleRepository.saveAndFlush(rule);
    }

    private ResultActions cards() throws Exception {
        return mockMvc.perform(get("/api/v1/cards"));
    }

    private ResultActions recommendations() throws Exception {
        return mockMvc.perform(
                get("/api/v1/recommendations?" + SPENDING));
    }

    private ResultActions breakEven() throws Exception {
        return mockMvc.perform(get("/api/v1/break-even?" + SPENDING
                + "&cardAId=" + card(RBC).getId()
                + "&cardBId=" + card(TANGERINE).getId()));
    }

    private ResultActions simulation() throws Exception {
        return mockMvc.perform(get("/api/v1/simulations/groceries?"
                + SPENDING + "&maxGroceries=1000&cardAId="
                + card(RBC).getId() + "&cardBId="
                + card(TANGERINE).getId()));
    }

    private void assertVisibleEverywhere() throws Exception {
        cards().andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cardName", hasItem(TANGERINE)));
        recommendations().andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cardName", hasItem(TANGERINE)));
        breakEven().andExpect(status().isOk());
        simulation().andExpect(status().isOk());
    }

    private void assertHiddenEverywhere() throws Exception {
        cards().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardName").value(RBC));
        recommendations().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].cardName", not(hasItem(TANGERINE))));
        breakEven().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Card B not found."));
        simulation().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Card B not found."));
    }

    @Test
    void validConfigurationIsVisibleInAllFourEndpoints() throws Exception {
        release();

        assertVisibleEverywhere();
        cards().andExpect(jsonPath(
                "$[?(@.cardName=='" + TANGERINE + "')].selectionPolicy")
                .exists());
    }

    @Test
    void missingDisplayMetadataHidesTheCardEverywhere() throws Exception {
        release();
        breakRequirementCode();

        assertHiddenEverywhere();
    }

    @Test
    void missingCategoryLabelHidesTheCardEverywhere() throws Exception {
        release();
        addUnlabelledSelectableCategory();

        assertHiddenEverywhere();
    }

    @Test
    void withheldCardStaysHiddenEverywhereRegardlessOfConfiguration()
            throws Exception {
        assertHiddenEverywhere();

        breakRequirementCode();
        assertHiddenEverywhere();
    }

    @Test
    void rbcKeepsItsOriginalBehaviourWhenAnotherCardIsIneligible()
            throws Exception {
        release();
        breakRequirementCode();

        recommendations()
                .andExpect(jsonPath("$[0].cardName").value(RBC))
                .andExpect(jsonPath("$[0].annualReward").value(216.0))
                .andExpect(jsonPath("$[0].selectionMode").doesNotExist());
    }
}
