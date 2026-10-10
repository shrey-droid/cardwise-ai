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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test rolls back, so un-withholding Tangerine never leaks out of a test.
@SpringBootTest(properties = "cardwise.catalogue.mode=REAL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SelectionComparisonApiTest {

    private static final String TANGERINE = "Tangerine Money-Back Credit Card";
    private static final String SPENDING =
            "groceries=600&gas=200&dining=300&travel=100&other=400";

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

    private long rbcId() {
        return card("RBC Cash Back Mastercard").getId();
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

    private ResultActions breakEven(String extra) throws Exception {
        return mockMvc.perform(get("/api/v1/break-even?" + SPENDING
                + "&cardAId=" + rbcId() + "&cardBId=" + tangerineId()
                + extra));
    }

    private ResultActions simulate(String extra) throws Exception {
        return mockMvc.perform(get("/api/v1/simulations/groceries?"
                + SPENDING + "&maxGroceries=1000&cardAId=" + rbcId()
                + "&cardBId=" + tangerineId() + extra));
    }

    private String selection(String categories) {
        return "&cardId=" + tangerineId() + "&selectedCategories=" + categories;
    }

    @Test
    void breakEvenUsesSelectedTangerineRates() throws Exception {
        releaseTangerineForThisTest();

        breakEven(selection("GAS,DINING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BREAK_EVEN_FOUND"))
                .andExpect(jsonPath("$.breakEvenMonthlyGroceries")
                        .value(433.33));
        breakEven(selection("GROCERIES,GAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_CROSSOVER"));
    }

    @Test
    void breakEvenWithNoSelectionTreatsGroceriesAsHalfPercent()
            throws Exception {
        releaseTangerineForThisTest();

        breakEven("&cardId=" + tangerineId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("NO_NONNEGATIVE_CROSSOVER"));
        breakEven("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("NO_NONNEGATIVE_CROSSOVER"));
    }

    @Test
    void breakEvenThreeCategoriesRequireConfirmation() throws Exception {
        releaseTangerineForThisTest();

        breakEven(selection("GROCERIES,GAS,DINING"))
                .andExpect(status().isBadRequest());
        breakEven(selection("GROCERIES,GAS,DINING")
                + "&extendedRequirementConfirmed=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_CROSSOVER"));
    }

    @Test
    void simulationAppliesTheSameSelectionAtEveryPoint() throws Exception {
        releaseTangerineForThisTest();
        String tangerineKey = "['" + tangerineId() + "']";
        String rbcKey = "['" + rbcId() + "']";

        // Tangerine = 150 + 0.06 * groceries; RBC = 72 + 0.24 * groceries.
        simulate(selection("GAS,DINING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BREAK_EVEN_FOUND"))
                .andExpect(jsonPath("$.points.length()").value(22))
                .andExpect(jsonPath("$.points[?(@.groceries==0.0)].rewards"
                        + tangerineKey).value(contains(150.0)))
                .andExpect(jsonPath("$.points[?(@.groceries==1000.0)].rewards"
                        + tangerineKey).value(contains(210.0)))
                .andExpect(jsonPath("$.points[?(@.groceries==1000.0)].rewards"
                        + rbcKey).value(contains(312.0)));
    }

    @Test
    void simulationWithoutSelectionUsesBaselineTangerine() throws Exception {
        releaseTangerineForThisTest();
        String tangerineKey = "['" + tangerineId() + "']";

        // Nothing selected: 60 + 0.06 * groceries.
        simulate("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[?(@.groceries==0.0)].rewards"
                        + tangerineKey).value(contains(60.0)));
    }

    @Test
    void invalidSelectionsAreRejectedByBothEndpoints() throws Exception {
        releaseTangerineForThisTest();

        for (String bad : new String[] {
                selection("GROCERIES,,GAS"), selection("FURNITURE"),
                selection("TRAVEL"), selection("GROCERIES,GAS,DINING,TRANSIT"),
                "&selectedCategories=GAS", "&extendedRequirementConfirmed=true",
                "&cardId=abc", "&cardId=0"}) {
            breakEven(bad).andExpect(status().isBadRequest());
            simulate(bad).andExpect(status().isBadRequest());
        }
    }

    @Test
    void withheldTangerineIsInaccessibleAndIndistinguishableFromUnknown()
            throws Exception {
        withholdTangerineForThisTest();

        String withheldBreakEven = breakEven(selection("GAS,DINING"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String withheldSimulation = simulate(selection("GAS,DINING"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        String unknownBreakEven = mockMvc.perform(get(
                "/api/v1/break-even?" + SPENDING + "&cardAId=" + rbcId()
                        + "&cardBId=999999&cardId=999999"
                        + "&selectedCategories=GAS,DINING"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String unknownSimulation = mockMvc.perform(get(
                "/api/v1/simulations/groceries?" + SPENDING
                        + "&maxGroceries=1000&cardAId=" + rbcId()
                        + "&cardBId=999999&cardId=999999"
                        + "&selectedCategories=GAS,DINING"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals(unknownBreakEven, withheldBreakEven);
        assertEquals(unknownSimulation, withheldSimulation);
    }

    @Test
    void selectionForAnUnrelatedCardDoesNotChangeTheResult()
            throws Exception {
        releaseTangerineForThisTest();

        String baseline = breakEven("").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String withUnknownCardSelection =
                breakEven("&cardId=999999&selectedCategories=GAS,DINING")
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
        String withRbcSelection =
                breakEven("&cardId=" + rbcId() + "&selectedCategories=GAS")
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();

        assertEquals(baseline, withUnknownCardSelection);
        assertEquals(baseline, withRbcSelection);
    }
}
