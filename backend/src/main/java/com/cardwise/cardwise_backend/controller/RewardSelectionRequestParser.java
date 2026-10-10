package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.service.RewardSelection;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Parses the optional cardId / selectedCategories / extendedRequirementConfirmed
 * request parameters. Card-specific eligibility is validated by the services.
 */
final class RewardSelectionRequestParser {

    private RewardSelectionRequestParser() {
    }

    static Map<Long, RewardSelection> parse(
            String cardId,
            String selectedCategories,
            String extendedRequirementConfirmed) {

        if (cardId == null) {
            if (selectedCategories != null
                    || extendedRequirementConfirmed != null) {
                throw new IllegalArgumentException(
                        "cardId is required with selection parameters."
                );
            }
            return Map.of();
        }

        long id;
        try {
            id = Long.parseLong(cardId.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "cardId must be a positive integer."
            );
        }
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "cardId must be a positive integer."
            );
        }

        Set<String> categories = Set.of();
        if (selectedCategories != null) {
            List<String> tokens = Arrays.stream(
                    selectedCategories.split(",", -1))
                    .map(String::trim)
                    .toList();
            if (tokens.stream().anyMatch(String::isEmpty)) {
                throw new IllegalArgumentException(
                        "selectedCategories must not contain blank entries."
                );
            }
            categories = tokens.stream().collect(Collectors.toSet());
        }

        boolean confirmed = false;
        if (extendedRequirementConfirmed != null) {
            String value = extendedRequirementConfirmed.trim();
            if (value.equalsIgnoreCase("true")) {
                confirmed = true;
            } else if (!value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException(
                        "extendedRequirementConfirmed must be true or false."
                );
            }
        }

        return Map.of(id, new RewardSelection(categories, confirmed));
    }
}
