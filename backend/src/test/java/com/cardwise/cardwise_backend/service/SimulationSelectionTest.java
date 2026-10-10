package com.cardwise.cardwise_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationSelectionTest {

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private BreakEvenService breakEvenService;

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService(
                recommendationService, breakEvenService);

        Map<String, Object> breakEven = new HashMap<>();
        breakEven.put("status", "NO_CROSSOVER");
        breakEven.put("breakEvenMonthlyGroceries", null);
        breakEven.put("recommendation", "Test");

        when(recommendationService.recommend(anyMap(), anyMap()))
                .thenReturn(List.of());
        when(breakEvenService.calculateBreakEven(
                eq(1L), eq(2L), anyMap(), anyMap()))
                .thenReturn(breakEven);
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

    @Test
    void sameSelectionsAreUsedAtEverySimulationPointAndForBreakEven() {
        Map<Long, RewardSelection> selections = Map.of(
                2L, new RewardSelection(Set.of("GROCERIES", "GAS"), false));

        simulationService.simulateGroceries(
                spending(), new BigDecimal("1000"), 1L, 2L, selections);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, RewardSelection>> captor =
                ArgumentCaptor.forClass(Map.class);
        verify(recommendationService, atLeast(21))
                .recommend(anyMap(), captor.capture());

        assertTrue(captor.getAllValues().size() >= 21);
        captor.getAllValues().forEach(
                passed -> assertEquals(selections, passed));

        verify(breakEvenService).calculateBreakEven(
                eq(1L), eq(2L), anyMap(), eq(selections));
    }

    @Test
    void legacyOverloadDelegatesWithEmptySelections() {
        simulationService.simulateGroceries(
                spending(), new BigDecimal("1000"), 1L, 2L);

        verify(recommendationService, atLeast(21))
                .recommend(anyMap(), eq(Map.of()));
        verify(breakEvenService).calculateBreakEven(
                eq(1L), eq(2L), anyMap(), eq(Map.of()));
    }

    @Test
    void nullSelectionsAreTreatedAsEmpty() {
        simulationService.simulateGroceries(
                spending(), new BigDecimal("1000"), 1L, 2L, null);

        verify(breakEvenService).calculateBreakEven(
                eq(1L), eq(2L), anyMap(), eq(Map.of()));
    }
}
