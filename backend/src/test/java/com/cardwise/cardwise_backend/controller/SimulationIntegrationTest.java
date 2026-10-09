package com.cardwise.cardwise_backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimulationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private ResultActions simulate(
            String cardAId,
            String cardBId
    ) throws Exception {
        return mockMvc.perform(
                get("/api/v1/simulations/groceries")
                        .param("cardAId", cardAId)
                        .param("cardBId", cardBId)
                        .param("groceries", "600")
                        .param("gas", "200")
                        .param("dining", "300")
                        .param("travel", "100")
                        .param("other", "400")
                        .param("maxGroceries", "1000")
        );
    }

    @Test
    void shouldReturn22PointsWithExactCrossover() throws Exception {
        simulate("2", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardAId").value(2))
                .andExpect(jsonPath("$.cardBId").value(1))
                .andExpect(jsonPath("$.status")
                        .value("BREAK_EVEN_FOUND"))
                .andExpect(jsonPath("$.breakEvenMonthlyGroceries")
                        .value(166.67))
                .andExpect(jsonPath("$.points.length()")
                        .value(22));
    }

    @Test
    void shouldSupportReversedCardSelection() throws Exception {
        simulate("1", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardAId").value(1))
                .andExpect(jsonPath("$.cardBId").value(2))
                .andExpect(jsonPath("$.breakEvenMonthlyGroceries")
                        .value(166.67))
                .andExpect(jsonPath("$.points.length()")
                        .value(22));
    }

    @Test
    void shouldReturnSelectedCardRewards() throws Exception {
        simulate("2", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].groceries").value(0))
                .andExpect(jsonPath("$.points[0].rewards['1']")
                        .value(120))
                .andExpect(jsonPath("$.points[0].rewards['2']")
                        .value(60))
                .andExpect(jsonPath("$.points[0].rewards['3']")
                        .doesNotExist());
    }

    @Test
    void shouldAcceptLegacyBreakEvenRequestWithFiveSpendingCategories()
            throws Exception {
        mockMvc.perform(get("/api/v1/break-even")
                        .param("cardAId", "2")
                        .param("cardBId", "1")
                        .param("groceries", "100")
                        .param("gas", "200")
                        .param("dining", "300")
                        .param("travel", "100")
                        .param("other", "400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakEvenMonthlyGroceries")
                        .value(166.67));
    }
}
