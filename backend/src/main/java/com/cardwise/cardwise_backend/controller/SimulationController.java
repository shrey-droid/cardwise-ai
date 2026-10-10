package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.service.SimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/groceries")
    public Map<String, Object> simulateGroceries(
            @RequestParam(defaultValue = "0") BigDecimal groceries,
            @RequestParam(defaultValue = "0") BigDecimal gas,
            @RequestParam(defaultValue = "0") BigDecimal dining,
            @RequestParam(defaultValue = "0") BigDecimal travel,
            @RequestParam(defaultValue = "0") BigDecimal other,
            @RequestParam(defaultValue = "0") BigDecimal transit,
            @RequestParam(defaultValue = "0") BigDecimal rideshare,
            @RequestParam(defaultValue = "0") BigDecimal evCharging,
            @RequestParam(defaultValue = "1000")
            BigDecimal maxGroceries,
            @RequestParam(defaultValue = "2") Long cardAId,
            @RequestParam(defaultValue = "1") Long cardBId,
            @RequestParam(required = false) String cardId,
            @RequestParam(required = false) String selectedCategories,
            @RequestParam(required = false)
            String extendedRequirementConfirmed) {

        Map<String, BigDecimal> monthlySpending = new HashMap<>();
        monthlySpending.put("GROCERIES", groceries);
        monthlySpending.put("GAS", gas);
        monthlySpending.put("DINING", dining);
        monthlySpending.put("TRAVEL", travel);
        monthlySpending.put("OTHER", other);
        monthlySpending.put("TRANSIT", transit);
        monthlySpending.put("RIDESHARE", rideshare);
        monthlySpending.put("EV_CHARGING", evCharging);

        return simulationService.simulateGroceries(
                monthlySpending,
            maxGroceries,
            cardAId,
            cardBId,
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
