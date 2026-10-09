package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.service.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(
            RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public List<Map<String, Object>> getRecommendations(
            @RequestParam BigDecimal groceries,
            @RequestParam BigDecimal gas,
            @RequestParam BigDecimal dining,
            @RequestParam BigDecimal travel,
            @RequestParam BigDecimal other) {

        Map<String, BigDecimal> monthlySpending = Map.of(
                "GROCERIES", groceries,
                "GAS", gas,
                "DINING", dining,
                "TRAVEL", travel,
                "OTHER", other
        );

        return recommendationService.recommend(monthlySpending);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidInput(
            IllegalArgumentException exception) {

        return Map.of("error", exception.getMessage());
    }
}