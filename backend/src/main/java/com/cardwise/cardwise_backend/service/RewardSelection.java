package com.cardwise.cardwise_backend.service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One card's selection state. Callers hold one per creditCardId.
 * extendedRequirementConfirmed means the user confirms the policy's extended
 * requirement is met (for Tangerine: cash back deposited to an eligible
 * Tangerine Savings Account), not merely that an account exists.
 */
public record RewardSelection(
        Set<String> categories,
        boolean extendedRequirementConfirmed
) {

    public static final RewardSelection NONE =
            new RewardSelection(Set.of(), false);

    public RewardSelection {
        categories = categories == null
                ? Set.of()
                : categories.stream()
                        .filter(category -> category != null)
                        .map(category ->
                                category.trim().toUpperCase(Locale.ROOT))
                        .filter(category -> !category.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }
}
