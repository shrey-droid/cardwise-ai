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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Not transactional: this verifies the committed state V12 leaves behind.
@SpringBootTest(properties = "cardwise.catalogue.mode=REAL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TangerineActivationApiTest {

    private static final String TANGERINE = "Tangerine Money-Back Credit Card";
    private static final String RBC = "RBC Cash Back Mastercard";
    private static final String SPENDING =
            "groceries=600&gas=200&dining=300&travel=100&other=400";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreditCardRepository creditCardRepository;

    private long id(String name) {
        return creditCardRepository.findAll().stream()
                .filter((CreditCard card) -> name.equals(card.getCardName()))
                .findFirst().orElseThrow().getId();
    }

    private static String forCard(String name) {
        return "$[?(@.cardName=='" + name + "')]";
    }

    private ResultActions recommend(String extra) throws Exception {
        return mockMvc.perform(get("/api/v1/recommendations?" + SPENDING + extra));
    }

    private String selection(String categories, boolean confirmed) {
        return "&cardId=" + id(TANGERINE)
                + (categories.isEmpty() ? "" : "&selectedCategories=" + categories)
                + "&extendedRequirementConfirmed=" + confirmed;
    }

    @Test
    void realCatalogueListsRbcAndTangerineButNoInternalFlag() throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cardName", containsInAnyOrder(RBC, TANGERINE)))
                .andExpect(jsonPath("$[*].demo", not(hasItem(true))))
                .andExpect(content().string(not(containsString("catalogueWithheld"))))
                .andExpect(jsonPath(forCard(RBC) + ".selectionPolicy", hasSize(0)));
    }

    @Test
    void tangerineExposesItsSelectionPolicy() throws Exception {
        String policy = forCard(TANGERINE) + ".selectionPolicy";

        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(jsonPath(policy + ".baseSelectionLimit", contains(2)))
                .andExpect(jsonPath(policy + ".extendedSelectionLimit", contains(3)))
                .andExpect(jsonPath(policy + ".changeHoldDays", contains(90)))
                .andExpect(jsonPath(policy + ".modelledCategoryCount", contains(4)))
                .andExpect(jsonPath(policy + ".offeredCategoryCount", contains(13)))
                .andExpect(jsonPath(policy + ".selectableCategories[*].code",
                        contains("GROCERIES", "GAS", "DINING", "TRANSIT")));
    }

    @Test
    void withoutASelectionTangerineIsABaselineIncompleteEstimate()
            throws Exception {
        recommend("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath(forCard(TANGERINE) + ".annualReward", contains(96.0)))
                .andExpect(jsonPath(forCard(TANGERINE) + ".selectionMode",
                        contains("NONE_SELECTED")))
                .andExpect(jsonPath(forCard(TANGERINE) + ".selectionRequired",
                        contains(true)))
                // RBC's ranking-relevant fields are unchanged and carry no selection state.
                .andExpect(jsonPath(forCard(RBC) + ".annualReward", contains(216.0)))
                .andExpect(jsonPath(forCard(RBC) + ".selectionMode", hasSize(0)));
    }

    @Test
    void selectionScenariosGiveTheExpectedTangerineRewards() throws Exception {
        Object[][] scenarios = {
                {"", false, 96.0, "NONE_SELECTED", true},
                {"GROCERIES", false, 204.0, "PARTIAL_SELECTION", true},
                {"GROCERIES,GAS", false, 240.0, "SELECTED", false},
                {"GROCERIES,GAS,DINING", true, 294.0, "SELECTED", false},
        };

        for (Object[] s : scenarios) {
            recommend(selection((String) s[0], (Boolean) s[1]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(forCard(TANGERINE) + ".annualReward",
                            contains((Double) s[2])))
                    .andExpect(jsonPath(forCard(TANGERINE) + ".selectionMode",
                            contains((String) s[3])))
                    .andExpect(jsonPath(forCard(TANGERINE) + ".selectionRequired",
                            contains((Boolean) s[4])))
                    .andExpect(jsonPath(forCard(RBC) + ".annualReward",
                            contains(216.0)));
        }
    }

    @Test
    void threeCategoriesStillRequireTheConfirmation() throws Exception {
        recommend(selection("GROCERIES,GAS,DINING", false))
                .andExpect(status().isBadRequest());
    }

    @Test
    void breakEvenUsesTheSelectedCategories() throws Exception {
        String pair = "&cardAId=" + id(RBC) + "&cardBId=" + id(TANGERINE);

        mockMvc.perform(get("/api/v1/break-even?" + SPENDING + pair
                        + selection("GAS,DINING", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BREAK_EVEN_FOUND"))
                .andExpect(jsonPath("$.breakEvenMonthlyGroceries").value(433.33));
        mockMvc.perform(get("/api/v1/break-even?" + SPENDING + pair
                        + selection("GROCERIES,GAS", false)))
                .andExpect(jsonPath("$.status").value("NO_CROSSOVER"));
        mockMvc.perform(get("/api/v1/break-even?" + SPENDING + pair))
                .andExpect(jsonPath("$.status").value("NO_NONNEGATIVE_CROSSOVER"));
    }

    @Test
    void simulationKeepsTheSelectionAtEveryPoint() throws Exception {
        String tangerine = "['" + id(TANGERINE) + "']";
        String rbc = "['" + id(RBC) + "']";
        String request = "/api/v1/simulations/groceries?gas=200&dining=300&travel=100"
                + "&other=400&maxGroceries=1000&cardAId=" + id(RBC)
                + "&cardBId=" + id(TANGERINE);

        // Groceries and Gas: Tangerine = 96 + 0.24 * groceries, RBC = 72 + 0.24 * groceries.
        mockMvc.perform(get(request + selection("GROCERIES,GAS", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].rewards" + tangerine).value(96.0))
                .andExpect(jsonPath("$.points[0].rewards" + rbc).value(72.0))
                .andExpect(jsonPath("$.points[20].rewards" + tangerine).value(336.0))
                .andExpect(jsonPath("$.points[20].rewards" + rbc).value(312.0));

        // No selection: Tangerine falls back to 0.5% everywhere, i.e. 60 + 0.06 * groceries.
        mockMvc.perform(get(request))
                .andExpect(jsonPath("$.points[0].rewards" + tangerine).value(60.0))
                .andExpect(jsonPath("$.points[20].rewards" + tangerine).value(120.0));
    }
}
