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
            @RequestParam BigDecimal other,
            @RequestParam(defaultValue = "0") BigDecimal transit,
            @RequestParam(defaultValue = "0") BigDecimal rideshare,
            @RequestParam(defaultValue = "0") BigDecimal evCharging,
            @RequestParam(required = false) String cardId,
            @RequestParam(required = false) String selectedCategories,
            // True means the user confirms the card's extended-selection
            // requirement is met (e.g. cash back deposited to savings).
            @RequestParam(required = false)
            String extendedRequirementConfirmed
        ) {

        Map<String, BigDecimal> monthlySpending = Map.of(
                "GROCERIES", groceries,
                "GAS", gas,
                "DINING", dining,
                "TRAVEL", travel,
            "OTHER", other,
            "TRANSIT", transit,
            "RIDESHARE", rideshare,
            "EV_CHARGING", evCharging
        );

        return recommendationService.recommend(
                monthlySpending,
                RewardSelectionRequestParser.parse(
                        cardId,
                        selectedCategories,
                        extendedRequirementConfirmed
                )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidInput(
            IllegalArgumentException exception) {

        return Map.of("error", exception.getMessage());
    }
}