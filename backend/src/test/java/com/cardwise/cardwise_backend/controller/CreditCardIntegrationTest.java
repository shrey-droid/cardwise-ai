package com.cardwise.cardwise_backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CreditCardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnAllSeededCards() throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(3))
                                .andExpect(jsonPath("$[0].demo").value(true))
                                .andExpect(jsonPath("$[1].demo").value(true))
                                .andExpect(jsonPath("$[2].demo").value(true));
    }

    @Test
    void shouldReturnOnlyCashbackCards() throws Exception {
        mockMvc.perform(get("/api/v1/cards")
                        .param("rewardType", "CASHBACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath(
                        "$[?(@.cardName == 'Everyday Cashback')]"
                ).exists())
                .andExpect(jsonPath(
                        "$[?(@.cardName == 'Grocery Rewards Plus')]"
                ).exists());
    }

    @Test
    void shouldExcludePointsCardsFromCashbackFilter() throws Exception {
        mockMvc.perform(get("/api/v1/cards")
                        .param("rewardType", "CASHBACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$[?(@.cardName == 'Travel Points Explorer')]"
                ).doesNotExist());
    }
}
