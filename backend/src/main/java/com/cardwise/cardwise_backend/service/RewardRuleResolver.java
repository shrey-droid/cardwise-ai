package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import com.cardwise.cardwise_backend.entity.RewardRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
public class RewardRuleResolver {

    /**
     * Resolves one card's rules into immutable effective rules. Entities are
     * only read, never modified. A null policy means the card has no
     * selectable categories and any selection is ignored.
     */
    public Map<String, EffectiveRewardRule> resolve(
            List<RewardRule> rules,
            CardSelectionPolicy policy,
            RewardSelection selection) {

        RewardSelection effectiveSelection =
                selection == null ? RewardSelection.NONE : selection;
        Set<String> selected = effectiveSelection.categories();

        if (policy != null) {
            validate(rules, policy, effectiveSelection);
        }

        Map<String, EffectiveRewardRule> resolved = new LinkedHashMap<>();
        for (RewardRule rule : rules) {
            String category = rule.getSpendingCategory();
            BigDecimal rate = rule.getRewardRate();

            if (policy != null && rule.isSelectable()
                    && !selected.contains(category)) {
                rate = rule.getUnselectedRewardRate();
            }

            resolved.put(category, new EffectiveRewardRule(
                    category,
                    rate,
                    rule.getBaseRewardRate(),
                    rule.getSpendingCap(),
                    rule.getCapPeriod()
            ));
        }

        return resolved;
    }

    private void validate(
            List<RewardRule> rules,
            CardSelectionPolicy policy,
            RewardSelection selection) {

        Set<String> selectable = new TreeSet<>();
        for (RewardRule rule : rules) {
            if (rule.isSelectable()) {
                selectable.add(rule.getSpendingCategory());
            }
        }

        for (String category : selection.categories()) {
            if (!SpendingCategories.ALL.contains(category)) {
                throw new IllegalArgumentException(
                        "Unknown spending category: " + category
                );
            }
            if (!selectable.contains(category)) {
                throw new IllegalArgumentException(
                        category + " is not a selectable category. "
                                + "Selectable categories: " + selectable
                );
            }
        }

        int count = selection.categories().size();
        int baseLimit = policy.getBaseSelectionLimit();
        int extendedLimit = policy.getExtendedSelectionLimit();

        if (count > extendedLimit) {
            throw new IllegalArgumentException(
                    "At most " + extendedLimit
                            + " categories can be selected."
            );
        }

        if (count > baseLimit && !selection.extendedRequirementConfirmed()) {
            throw new IllegalArgumentException(
                    "Selecting more than " + baseLimit
                            + " categories requires: "
                            + policy.getExtendedRequirement()
            );
        }
    }
}
