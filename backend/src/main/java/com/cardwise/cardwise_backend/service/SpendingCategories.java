package com.cardwise.cardwise_backend.service;

import java.util.List;

public final class SpendingCategories {

    public static final String GROCERIES = "GROCERIES";

    public static final List<String> ALL = List.of(
            GROCERIES,
            "GAS",
            "DINING",
            "TRAVEL",
            "OTHER"
    );

    public static final List<String> FIXED = ALL.stream()
            .filter(category -> !GROCERIES.equals(category))
            .toList();

    private SpendingCategories() {
    }
}
