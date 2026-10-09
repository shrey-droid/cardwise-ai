
package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.service.BreakEvenService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/break-even")
public class BreakEvenController {

    private final BreakEvenService breakEvenService;

    public BreakEvenController(
            BreakEvenService breakEvenService
    ) {
        this.breakEvenService = breakEvenService;
    }

    @GetMapping
    public Map<String, Object> getBreakEven(
            @RequestParam Long cardAId,
            @RequestParam Long cardBId,
            @RequestParam BigDecimal groceries,
            @RequestParam BigDecimal gas,
            @RequestParam BigDecimal dining,
            @RequestParam BigDecimal travel,
                        @RequestParam BigDecimal other,
                        @RequestParam(defaultValue = "0") BigDecimal transit,
                        @RequestParam(defaultValue = "0") BigDecimal rideshare,
                        @RequestParam(defaultValue = "0") BigDecimal evCharging
    ) {
        Map<String, BigDecimal> spending = Map.of(
                "GROCERIES", groceries,
                "GAS", gas,
                "DINING", dining,
                "TRAVEL", travel,
                                "OTHER", other,
                                "TRANSIT", transit,
                                "RIDESHARE", rideshare,
                                "EV_CHARGING", evCharging
        );

        return breakEvenService.calculateBreakEven(
                cardAId,
                cardBId,
                spending
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(
            IllegalArgumentException exception
    ) {
        return Map.of("error", exception.getMessage());
    }
}
