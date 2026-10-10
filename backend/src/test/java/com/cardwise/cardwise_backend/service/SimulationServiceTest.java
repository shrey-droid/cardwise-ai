package com.cardwise.cardwise_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private BreakEvenService breakEvenService;

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService(
                recommendationService,
                breakEvenService
        );
    }

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

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(String expected, Object actual) {
        assertNotNull(actual);
        assertEquals(
                0,
                amount(expected).compareTo(
                        new BigDecimal(actual.toString())
                )
        );
    }

    private void mockRecommendations() {
        when(recommendationService.recommend(anyMap(), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, BigDecimal> scenario =
                            invocation.getArgument(0);
                    BigDecimal groceries =
                            scenario.get("GROCERIES");

                    // Everyday Cashback: $120 + 12% of monthly groceries.
                    BigDecimal everydayNet = amount("120")
                            .add(groceries.multiply(amount("0.12")));

                    // Grocery Rewards Plus: $60 + 48% of monthly groceries.
                    BigDecimal groceryPlusNet = amount("60")
                            .add(groceries.multiply(amount("0.48")));

                    return List.of(
                            Map.of(
                                    "cardId", 1L,
                                    "netAnnualReward", everydayNet
                            ),
                            Map.of(
                                    "cardId", 2L,
                                    "netAnnualReward", groceryPlusNet
                            )
                    );
                });
    }

    private void mockBreakEven(
            Long cardAId,
            Long cardBId,
            String status,
            String threshold
    ) {
        // Create the result before Mockito stubbing.
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put(
                "breakEvenMonthlyGroceries",
                threshold == null ? null : amount(threshold)
        );
        result.put("recommendation", "Test recommendation");

        when(breakEvenService.calculateBreakEven(
                eq(cardAId),
                eq(cardBId),
                anyMap(),
                anyMap()
        )).thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> points(
            Map<String, Object> result
    ) {
        return (List<Map<String, Object>>) result.get("points");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rewards(
            Map<String, Object> point
    ) {
        return (Map<String, Object>) point.get("rewards");
    }

    @Test
    void shouldGenerate21EvenlySpacedPointsWithoutCrossover() {
        mockRecommendations();
        mockBreakEven(2L, 1L, "NO_CROSSOVER", null);

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(),
                        amount("1000"),
                        2L,
                        1L
                );

        List<Map<String, Object>> simulationPoints = points(result);
        assertEquals(21, simulationPoints.size());

        for (int i = 0; i <= 20; i++) {
            assertAmount(
                    String.valueOf(i * 50),
                    simulationPoints.get(i).get("groceries")
            );
        }

        assertEquals("GROCERIES", result.get("category"));
        assertEquals(2L, result.get("cardAId"));
        assertEquals(1L, result.get("cardBId"));
    }

    @Test
    void shouldIncludeExactBreakEvenPoint() {
        mockRecommendations();
        mockBreakEven(2L, 1L, "BREAK_EVEN_FOUND", "166.67");

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(), amount("1000"), 2L, 1L
                );

        List<Map<String, Object>> simulationPoints = points(result);
        assertEquals(22, simulationPoints.size());
        assertTrue(simulationPoints.stream().anyMatch(point ->
                amount("166.67").compareTo(
                        (BigDecimal) point.get("groceries")
                ) == 0
        ));
        assertAmount("166.67", result.get("breakEvenMonthlyGroceries"));
        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertEquals("BREAK_EVEN_FOUND", result.get("breakEvenStatus"));
    }

    @Test
    void shouldReturnRewardsUnderCorrectCardIds() {
        mockRecommendations();
        mockBreakEven(2L, 1L, "BREAK_EVEN_FOUND", "166.67");

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(), amount("1000"), 2L, 1L
                );

        Map<String, Object> firstPoint = points(result).get(0);
        Map<String, Object> pointRewards = rewards(firstPoint);

        assertAmount("120", pointRewards.get("1"));
        assertAmount("60", pointRewards.get("2"));
        assertEquals(2, pointRewards.size());
    }

    @Test
    void shouldSupportReversedCardSelection() {
        mockRecommendations();
        mockBreakEven(1L, 2L, "BREAK_EVEN_FOUND", "166.67");

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(), amount("1000"), 1L, 2L
                );

        assertEquals(1L, result.get("cardAId"));
        assertEquals(2L, result.get("cardBId"));
        assertAmount("166.67", result.get("breakEvenMonthlyGroceries"));
        assertEquals(22, points(result).size());

        verify(breakEvenService).calculateBreakEven(
                eq(1L), eq(2L), anyMap(), anyMap()
        );
    }

    @Test
    void shouldNotDuplicateExistingBreakEvenPoint() {
        mockRecommendations();
        mockBreakEven(2L, 1L, "BREAK_EVEN_FOUND", "200");

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(), amount("1000"), 2L, 1L
                );

        List<Map<String, Object>> simulationPoints = points(result);
        assertEquals(21, simulationPoints.size());

        long matchingPoints = simulationPoints.stream()
                .filter(point -> amount("200").compareTo(
                        (BigDecimal) point.get("groceries")
                ) == 0)
                .count();

        assertEquals(1, matchingPoints);
    }

    @Test
    void shouldHandleCrossoverOutsideSimulationRange() {
        mockRecommendations();
        mockBreakEven(2L, 1L, "BREAK_EVEN_FOUND", "166.67");

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(), amount("100"), 2L, 1L
                );

        assertEquals(21, points(result).size());
        assertAmount("166.67", result.get("breakEvenMonthlyGroceries"));
    }

    @Test
    void shouldHandleNoNonnegativeCrossover() {
        mockRecommendations();
        mockBreakEven(2L, 1L, "NO_NONNEGATIVE_CROSSOVER", null);

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(), amount("1000"), 2L, 1L
                );

        assertEquals("NO_NONNEGATIVE_CROSSOVER", result.get("status"));
        assertNull(result.get("breakEvenMonthlyGroceries"));
        assertEquals(21, points(result).size());
        assertEquals("Test recommendation", result.get("recommendation"));
        assertEquals(
                "Test recommendation",
                result.get("breakEvenRecommendation")
        );
    }

    @Test
    void shouldRejectDuplicateCardIdsImmediately() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> simulationService.simulateGroceries(
                                spending(),
                                amount("1000"),
                                1L,
                                1L
                        )
                );

        assertEquals(
                "Please select two different cards.",
                exception.getMessage()
        );

        verifyNoInteractions(
                recommendationService,
                breakEvenService
        );
    }

    @Test
    void shouldReturnOnlySelectedCardRewards() {
        when(recommendationService.recommend(anyMap(), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "cardId", 1L,
                                "netAnnualReward", amount("120")
                        ),
                        Map.of(
                                "cardId", 2L,
                                "netAnnualReward", amount("60")
                        ),
                        Map.of(
                                "cardId", 3L,
                                "netAnnualReward", amount("90")
                        )
                ));

        mockBreakEven(2L, 1L, "NO_CROSSOVER", null);

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(), amount("1000"), 2L, 1L
                );

        assertEquals(21, points(result).size());

        for (Map<String, Object> point : points(result)) {
            Map<String, Object> rewardData = rewards(point);

            assertEquals(2, rewardData.size());
            assertTrue(rewardData.containsKey("1"));
            assertTrue(rewardData.containsKey("2"));
            assertFalse(rewardData.containsKey("3"));
        }
    }

    @Test
    void shouldInsertBothCrossoverPoints() {
        mockRecommendations();

        Map<String, Object> breakEvenResult = new HashMap<>();
        breakEvenResult.put("status", "MULTIPLE_CROSSOVERS");
        breakEvenResult.put(
                "breakEvenMonthlyGroceries",
                amount("166.67")
        );
        breakEvenResult.put(
                "breakEvenPoints",
                List.of(
                        amount("166.67"),
                        amount("1166.67")
                )
        );
        breakEvenResult.put(
                "recommendation",
                "Two crossover points found."
        );

        when(breakEvenService.calculateBreakEven(
                eq(2L),
                eq(1L),
                anyMap(),
                anyMap()
        )).thenReturn(breakEvenResult);

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(),
                        amount("1500"),
                        2L,
                        1L
                );

        List<Map<String, Object>> simulationPoints = points(result);

        // 21 regular points + 2 crossover points.
        assertEquals(23, simulationPoints.size());

        assertTrue(simulationPoints.stream().anyMatch(point ->
                amount("166.67").compareTo(
                        (BigDecimal) point.get("groceries")
                ) == 0
        ));

        assertTrue(simulationPoints.stream().anyMatch(point ->
                amount("1166.67").compareTo(
                        (BigDecimal) point.get("groceries")
                ) == 0
        ));

        assertEquals("MULTIPLE_CROSSOVERS", result.get("status"));
        assertAmount("166.67", result.get("breakEvenMonthlyGroceries"));

        @SuppressWarnings("unchecked")
        List<BigDecimal> crossovers =
                (List<BigDecimal>) result.get("breakEvenPoints");

        assertEquals(2, crossovers.size());
        assertAmount("166.67", crossovers.get(0));
        assertAmount("1166.67", crossovers.get(1));
    }

    @Test
    void shouldPropagateTieIntervalsToSimulationResponse() {
        mockRecommendations();

        Map<String, Object> breakEvenResult = new HashMap<>();
        breakEvenResult.put("status", "TIE_INTERVAL");
        breakEvenResult.put(
                "recommendation",
                "Both cards are equal above $500."
        );
        breakEvenResult.put(
                "breakEvenMonthlyGroceries",
                amount("500.00")
        );
        breakEvenResult.put("breakEvenPoints", List.of());

        Map<String, BigDecimal> tieInterval = new HashMap<>();
        tieInterval.put("start", amount("500.00"));
        tieInterval.put("end", null);
        breakEvenResult.put("tieIntervals", List.of(tieInterval));

        when(breakEvenService.calculateBreakEven(
                eq(1L),
                eq(2L),
                anyMap(),
                anyMap()
        )).thenReturn(breakEvenResult);

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(),
                        amount("1000"),
                        1L,
                        2L
                );

        assertEquals("TIE_INTERVAL", result.get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, BigDecimal>> intervals =
                (List<Map<String, BigDecimal>>) result.get("tieIntervals");

        assertNotNull(intervals);
        assertEquals(1, intervals.size());
        assertAmount("500.00", intervals.get(0).get("start"));
        assertNull(intervals.get(0).get("end"));

        @SuppressWarnings("unchecked")
        List<BigDecimal> crossoverPoints =
                (List<BigDecimal>) result.get("breakEvenPoints");

        assertTrue(crossoverPoints.isEmpty());
        assertEquals(21, points(result).size());
    }

    @Test
    void shouldDefaultMissingTieIntervalsToEmptyList() {
        mockRecommendations();

        Map<String, Object> breakEvenResult = new HashMap<>();
        breakEvenResult.put("status", "BREAK_EVEN_FOUND");
        breakEvenResult.put("recommendation", "Card A becomes better.");
        breakEvenResult.put(
                "breakEvenMonthlyGroceries",
                amount("500.00")
        );
        breakEvenResult.put(
                "breakEvenPoints",
                List.of(amount("500.00"))
        );

        when(breakEvenService.calculateBreakEven(
                eq(1L),
                eq(2L),
                anyMap(),
                anyMap()
        )).thenReturn(breakEvenResult);

        Map<String, Object> result =
                simulationService.simulateGroceries(
                        spending(),
                        amount("1000"),
                        1L,
                        2L
                );

        assertEquals("BREAK_EVEN_FOUND", result.get("status"));
        assertEquals(List.of(), result.get("tieIntervals"));

        @SuppressWarnings("unchecked")
        List<BigDecimal> crossoverPoints =
                (List<BigDecimal>) result.get("breakEvenPoints");

        assertEquals(1, crossoverPoints.size());
        assertAmount("500.00", crossoverPoints.get(0));
        assertEquals(21, points(result).size());
    }

    @Test
    void shouldRejectInvalidInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> simulationService.simulateGroceries(
                        null, amount("1000"), 2L, 1L
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> simulationService.simulateGroceries(
                        spending(), amount("1000"), null, 1L
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> simulationService.simulateGroceries(
                        spending(), BigDecimal.ZERO, 2L, 1L
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> simulationService.simulateGroceries(
                        spending(), amount("-100"), 2L, 1L
                )
        );

        Map<String, BigDecimal> negativeSpending =
                new HashMap<>(spending());
        negativeSpending.put("GAS", amount("-50"));

        assertThrows(
                IllegalArgumentException.class,
                () -> simulationService.simulateGroceries(
                        negativeSpending, amount("1000"), 2L, 1L
                )
        );

        verifyNoInteractions(
                recommendationService,
                breakEvenService
        );
    }
}
