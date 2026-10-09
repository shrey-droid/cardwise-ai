package com.cardwise.cardwise_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RecommendationIntegrationTest {

    @Autowired
    private RecommendationService recommendationService;

    private Map<String, BigDecimal> spending() {
        return Map.of(
                "GROCERIES", new BigDecimal("600"),
                "GAS", new BigDecimal("200"),
                "DINING", new BigDecimal("300"),
                "TRAVEL", new BigDecimal("100"),
                "OTHER", new BigDecimal("400")
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
}
