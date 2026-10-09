package com.cardwise.cardwise_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationIntegrationTest {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private MockMvc mockMvc;

    private Map<String, BigDecimal> spending() {
        return Map.of(
                "GROCERIES", new BigDecimal("600"),
                "GAS", new BigDecimal("200"),
                "DINING", new BigDecimal("300"),
                "TRAVEL", new BigDecimal("100"),
                "OTHER", new BigDecimal("400"),
                "TRANSIT", BigDecimal.ZERO,
                "RIDESHARE", BigDecimal.ZERO,
                "EV_CHARGING", BigDecimal.ZERO
        );
    }

    private void assertAmount(String expected, Object actual) {
        assertNotNull(actual);
        assertEquals(
                0,
                new BigDecimal(expected)
                        .compareTo((BigDecimal) actual)
        );
    }

    @Test
    void shouldCalculateAndRankCashbackFromDatabase() {
        List<Map<String, Object>> results =
                recommendationService.recommend(spending());

        assertEquals(2, results.size());

        Map<String, Object> first = results.get(0);
        Map<String, Object> second = results.get(1);

        assertEquals("Grocery Rewards Plus", first.get("cardName"));
        assertEquals("Everyday Cashback", second.get("cardName"));

        assertAmount("468.00", first.get("annualReward"));
        assertAmount("120.00", first.get("annualFee"));
        assertAmount("348.00", first.get("netAnnualReward"));

        assertAmount("192.00", second.get("annualReward"));
        assertAmount("0.00", second.get("annualFee"));
        assertAmount("192.00", second.get("netAnnualReward"));
    }

    @Test
    void shouldExcludePointsCardsFromRecommendations() {
        List<Map<String, Object>> results =
                recommendationService.recommend(spending());

        assertTrue(results.stream().noneMatch(card ->
                "Travel Points Explorer".equals(card.get("cardName"))
        ));
    }

        @Test
        void shouldAcceptLegacyFiveCategoryRecommendationRequest()
            throws Exception {
        mockMvc.perform(get("/api/v1/recommendations")
                .param("groceries", "0")
                .param("gas", "0")
                .param("dining", "0")
                .param("travel", "0")
                .param("other", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].rewardBreakdown.EV_CHARGING")
                .value(0.0));
        }

        @Test
        void shouldApplyEvChargingSpendingToItsRewardCategory()
            throws Exception {
        mockMvc.perform(get("/api/v1/recommendations")
                .param("groceries", "0")
                .param("gas", "0")
                .param("dining", "0")
                .param("travel", "0")
                .param("other", "0")
                .param("transit", "0")
                .param("rideshare", "0")
                .param("evCharging", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].rewardBreakdown.EV_CHARGING")
                .value(12.0));
        }
}
