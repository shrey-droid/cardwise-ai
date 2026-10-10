package com.cardwise.cardwise_backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SimulationService {

    private static final BigDecimal TWENTY = BigDecimal.valueOf(20);

    private final RecommendationService recommendationService;
        private final BreakEvenService breakEvenService;

    public SimulationService(
                        RecommendationService recommendationService,
                        BreakEvenService breakEvenService) {
        this.recommendationService = recommendationService;
                this.breakEvenService = breakEvenService;
    }

    public Map<String, Object> simulateGroceries(
            Map<String, BigDecimal> monthlySpending,
                        BigDecimal maxGroceries,
                        Long cardAId,
                        Long cardBId) {
        return simulateGroceries(
                monthlySpending, maxGroceries, cardAId, cardBId, Map.of());
    }

    // The same selections apply at every grocery-spending point.
    public Map<String, Object> simulateGroceries(
            Map<String, BigDecimal> monthlySpending,
                        BigDecimal maxGroceries,
                        Long cardAId,
                        Long cardBId,
                        Map<Long, RewardSelection> selectionsByCardId) {

        Map<Long, RewardSelection> selections =
                selectionsByCardId == null ? Map.of() : selectionsByCardId;

        if (monthlySpending == null) {
            throw new IllegalArgumentException(
                    "Monthly spending is required.");
        }

        if (cardAId == null || cardBId == null) {
            throw new IllegalArgumentException(
                    "Two card IDs are required.");
        }

        if (cardAId.equals(cardBId)) {
            throw new IllegalArgumentException(
                    "Please select two different cards."
            );
        }

        if (maxGroceries == null ||
                maxGroceries.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "maxGroceries must be greater than zero.");
        }

        if (monthlySpending.values().stream()
                .anyMatch(value -> value == null ||
                        value.compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException(
                    "Spending amounts cannot be negative.");
        }

        List<Map<String, Object>> points = new ArrayList<>();

        // Generate 21 evenly spaced grocery-spending scenarios.
        for (int i = 0; i <= 20; i++) {
            BigDecimal groceries = maxGroceries
                    .multiply(BigDecimal.valueOf(i))
                    .divide(TWENTY, 2, RoundingMode.HALF_UP);

            points.add(calculatePoint(
                    monthlySpending,
                    groceries,
                    cardAId,
                    cardBId,
                    selections
            ));
        }

        Map<String, Object> breakEvenResult =
                breakEvenService.calculateBreakEven(
                        cardAId,
                        cardBId,
                        monthlySpending,
                        selections
                );

                // Preserve the original single-crossover field.
                BigDecimal breakEven = toBigDecimal(
                                breakEvenResult.get("breakEvenMonthlyGroceries")
                );

                // New API responses may contain multiple crossover points.
                // Older mocked responses may only contain the original field.
                List<BigDecimal> breakEvenPoints = new ArrayList<>();
                Object rawPoints = breakEvenResult.get("breakEvenPoints");

                if (rawPoints instanceof List<?> rawList) {
                        for (Object rawPoint : rawList) {
                                if (rawPoint != null) {
                                        breakEvenPoints.add(toBigDecimal(rawPoint));
                                }
                        }
                } else if (breakEven != null) {
                        breakEvenPoints.add(breakEven);
                }

                // Normalize the crossover list: sorted and unique.
                breakEvenPoints = breakEvenPoints.stream()
                                .distinct()
                                .sorted()
                                .toList();

                // Insert every crossover inside the simulation range.
                for (BigDecimal crossover : breakEvenPoints) {
                        if (crossover.compareTo(BigDecimal.ZERO) < 0 ||
                                        crossover.compareTo(maxGroceries) > 0) {
                                continue;
                        }

                        boolean alreadyExists = points.stream().anyMatch(
                                        point -> new BigDecimal(
                                                        point.get("groceries").toString()
                                        ).compareTo(crossover) == 0
                        );

                        if (!alreadyExists) {
                                points.add(calculatePoint(
                                                monthlySpending,
                                                crossover,
                                                cardAId,
                                                cardBId,
                                                selections
                                ));
                        }
                }

        points.sort((a, b) -> {
            BigDecimal aGroceries =
                    new BigDecimal(a.get("groceries").toString());
            BigDecimal bGroceries =
                    new BigDecimal(b.get("groceries").toString());
            return aGroceries.compareTo(bGroceries);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", "GROCERIES");
        result.put("maxGroceries", maxGroceries);
        result.put("cardAId", cardAId);
        result.put("cardBId", cardBId);
        result.put("breakEvenMonthlyGroceries", breakEven);
        result.put("breakEvenPoints", breakEvenPoints);
        Object rawTieIntervals = breakEvenResult.get("tieIntervals");
        result.put(
                "tieIntervals",
                rawTieIntervals instanceof List<?>
                        ? rawTieIntervals
                        : List.of()
        );
        result.put("status", breakEvenResult.get("status"));
        result.put("recommendation", breakEvenResult.get("recommendation"));
        // Keep the earlier response keys available for existing clients.
        result.put("breakEvenStatus", breakEvenResult.get("status"));
        result.put(
                "breakEvenRecommendation",
                breakEvenResult.get("recommendation")
        );
        result.put("points", points);

        return result;
    }

    private Map<String, Object> calculatePoint(
            Map<String, BigDecimal> monthlySpending,
            BigDecimal groceries,
            Long cardAId,
            Long cardBId,
            Map<Long, RewardSelection> selections) {

        Map<String, BigDecimal> scenario =
                new HashMap<>(monthlySpending);
        scenario.put("GROCERIES", groceries);

        List<Map<String, Object>> recommendations =
                recommendationService.recommend(scenario, selections);

        Map<String, Object> rewards = new LinkedHashMap<>();
        for (Map<String, Object> card : recommendations) {
                        Long id = ((Number) card.get("cardId")).longValue();

                        if (id.equals(cardAId) || id.equals(cardBId)) {
                                rewards.put(
                                                String.valueOf(id),
                                                card.get("netAnnualReward")
                                );
                        }
        }

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("groceries", groceries);
        point.put("rewards", rewards);

        return point;
    }

        private BigDecimal toBigDecimal(Object value) {
                if (value == null) {
                        return null;
                }

                if (value instanceof BigDecimal decimal) {
                        return decimal;
                }

                return new BigDecimal(value.toString());
        }
}
