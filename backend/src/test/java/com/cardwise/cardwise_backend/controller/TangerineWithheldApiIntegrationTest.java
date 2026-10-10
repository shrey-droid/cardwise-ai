package com.cardwise.cardwise_backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cardwise.catalogue.mode=REAL")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TangerineWithheldApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void withheldTangerineIsNotListedOrRecommendedInRealMode()
            throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cardName",
                        not(hasItem("Tangerine Money-Back Credit Card"))));

        mockMvc.perform(get("/api/v1/recommendations")
                        .param("groceries", "600")
                        .param("gas", "200")
                        .param("dining", "300")
                        .param("travel", "100")
                        .param("other", "400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cardName",
                        not(hasItem("Tangerine Money-Back Credit Card"))));
    }
}
