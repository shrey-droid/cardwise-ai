package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test rolls back, so un-withholding Tangerine never leaks out of a test.
@SpringBootTest(properties = "cardwise.catalogue.mode=REAL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecommendationSelectionApiTest {

    private static final String TANGERINE = "Tangerine Money-Back Credit Card";
    private static final String TANGERINE_AMOUNT =
            "$[?(@.cardName=='" + TANGERINE + "')]";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreditCardRepository creditCardRepository;

    private CreditCard card(String name) {
        return creditCardRepository.findAll().stream()
                .filter(card -> name.equals(card.getCardName()))
                .findFirst()
                .orElseThrow();
    }

    private long tangerineId() {
        return card(TANGERINE).getId();
    }

    private void releaseTangerineForThisTest() {
        CreditCard tangerine = card(TANGERINE);
        tangerine.setCatalogueWithheld(false);
        creditCardRepository.saveAndFlush(tangerine);
    }

    private ResultActions recommend(String extraQuery) throws Exception {
        return mockMvc.perform(get("/api/v1/recommendations?groceries=600"
                + "&gas=200&dining=300&travel=100&other=400" + extraQuery));
    }

    private String body(String extraQuery) throws Exception {
        return recommend(extraQuery).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void requestWithoutSelectionParametersIsUnchanged() throws Exception {
        recommend("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardName")
                        .value("RBC Cash Back Mastercard"))
                .andExpect(jsonPath("$[0].annualReward").value(216.0))
                .andExpect(jsonPath("$[0].selectionMode").doesNotExist());
    }

    @Test
    void rbcIsUnchangedWhenASelectionIsSuppliedForIt() throws Exception {
        long rbcId = card("RBC Cash Back Mastercard").getId();

        assertEquals(body(""), body("&cardId=" + rbcId
                + "&selectedCategories=GAS"));
    }

    @Test
    void twoCategorySelectionIsAppliedToTangerine() throws Exception {
        releaseTangerineForThisTest();

        recommend("&cardId=" + tangerineId()
                + "&selectedCategories=GROCERIES,GAS"
                + "&extendedRequirementConfirmed=false")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".annualReward")
                        .value(contains(240.0)))
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".selectionMode")
                        .value(contains("SELECTED")))
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".selectionRequired")
                        .value(contains(false)))
                .andExpect(jsonPath("$[0].selectedCategories[0]")
                        .value("GAS"))
                .andExpect(jsonPath("$[0].selectedCategories[1]")
                        .value("GROCERIES"));
    }

    @Test
    void threeCategorySelectionWithConfirmationIsAccepted()
            throws Exception {
        releaseTangerineForThisTest();

        recommend("&cardId=" + tangerineId()
                + "&selectedCategories=GROCERIES,GAS,DINING"
                + "&extendedRequirementConfirmed=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".annualReward")
                        .value(contains(294.0)));
    }

    @Test
    void cardIdAloneReportsNoneSelectedAndBaselineRewards()
            throws Exception {
        releaseTangerineForThisTest();

        recommend("&cardId=" + tangerineId())
                .andExpect(status().isOk())
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".annualReward")
                        .value(contains(96.0)))
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".selectionMode")
                        .value(contains("NONE_SELECTED")))
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".selectionRequired")
                        .value(contains(true)));
    }

    @Test
    void singleCategoryIsPartialSelection() throws Exception {
        releaseTangerineForThisTest();

        recommend("&cardId=" + tangerineId() + "&selectedCategories=GAS")
                .andExpect(status().isOk())
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".selectionMode")
                        .value(contains("PARTIAL_SELECTION")))
                .andExpect(jsonPath(TANGERINE_AMOUNT + ".selectionRequired")
                        .value(contains(true)));
    }

    @Test
    void selectionParametersWithoutCardIdAreRejected() throws Exception {
        recommend("&selectedCategories=GROCERIES")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("cardId")));
        recommend("&extendedRequirementConfirmed=true")
                .andExpect(status().isBadRequest());
        recommend("&extendedRequirementConfirmed=false")
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedOrNonPositiveCardIdIsRejected() throws Exception {
        for (String invalid : new String[] {"abc", "0", "-5", "1.5", ""}) {
            recommend("&cardId=" + invalid)
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void malformedConfirmationFlagIsRejected() throws Exception {
        recommend("&cardId=" + tangerineId()
                + "&extendedRequirementConfirmed=maybe")
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankCategoryTokensAreRejected() throws Exception {
        for (String invalid : new String[] {
                "GROCERIES,,GAS", "GROCERIES,", ",GAS", "", " "}) {
            recommend("&cardId=" + tangerineId()
                    + "&selectedCategories=" + invalid)
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void unknownAndNonSelectableCategoriesAreRejectedForAVisibleCard()
            throws Exception {
        releaseTangerineForThisTest();

        recommend("&cardId=" + tangerineId() + "&selectedCategories=FURNITURE")
                .andExpect(status().isBadRequest());
        recommend("&cardId=" + tangerineId() + "&selectedCategories=TRAVEL")
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooManyCategoriesAreRejected() throws Exception {
        releaseTangerineForThisTest();

        recommend("&cardId=" + tangerineId()
                + "&selectedCategories=GROCERIES,GAS,DINING")
                .andExpect(status().isBadRequest());
        recommend("&cardId=" + tangerineId()
                + "&selectedCategories=GROCERIES,GAS,DINING,TRANSIT"
                + "&extendedRequirementConfirmed=true")
                .andExpect(status().isBadRequest());
    }

    @Test
    void selectionsForAWithheldCardDoNotExposeOrChangeAnything()
            throws Exception {
        String baseline = body("");
        long id = tangerineId();

        assertEquals(baseline, body("&cardId=" + id
                + "&selectedCategories=GROCERIES,GAS"));
        assertEquals(baseline, body("&cardId=" + id
                + "&selectedCategories=TRAVEL"));
        assertEquals(baseline, body("&cardId=999999"
                + "&selectedCategories=GROCERIES,GAS"));

        recommend("&cardId=" + id + "&selectedCategories=GROCERIES,GAS")
                .andExpect(jsonPath("$[*].cardName", not(hasItem(TANGERINE))));
    }
}
