package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DEMO mode must be unaffected by V12 activating a real card.
@SpringBootTest(properties = "cardwise.catalogue.mode=DEMO")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoCatalogueAfterActivationApiTest {

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

    @Test
    void demoCatalogueStillHasTheThreeOriginalCardsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cardName", containsInAnyOrder(
                        "Everyday Cashback", "Grocery Rewards Plus",
                        "Travel Points Explorer")))
                .andExpect(content().string(not(containsString("selectionPolicy"))))
                .andExpect(content().string(not(containsString("catalogueWithheld"))));
    }

    @Test
    void demoRecommendationsAndBreakEvenAreUnchanged() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations?" + SPENDING))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].cardName").value("Grocery Rewards Plus"))
                .andExpect(jsonPath("$[0].annualReward").value(468.0))
                .andExpect(jsonPath("$[0].netAnnualReward").value(348.0))
                .andExpect(jsonPath("$[1].cardName").value("Everyday Cashback"))
                .andExpect(jsonPath("$[1].annualReward").value(192.0));

        mockMvc.perform(get("/api/v1/break-even?" + SPENDING
                        + "&cardAId=" + id("Grocery Rewards Plus")
                        + "&cardBId=" + id("Everyday Cashback")))
                .andExpect(jsonPath("$.status").value("BREAK_EVEN_FOUND"))
                .andExpect(jsonPath("$.breakEvenMonthlyGroceries").value(166.67));
    }
}
