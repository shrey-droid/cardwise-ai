package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CardSelectionPolicyRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;
import com.cardwise.cardwise_backend.service.SelectionPolicyView.SelectableCategoryView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SelectionPolicyViewService {

    private static final Logger log =
            LoggerFactory.getLogger(SelectionPolicyViewService.class);

    private record ProgramDisplay(
            String requirementLabel,
            int offeredCategoryCount,
            Map<String, String> categoryLabels
    ) {
    }

    // Temporary: the issuer's full category catalogue and display wording are
    // not stored in the database. Keyed by the policy's requirement code, never
    // by card name or ID.
    private static final Map<String, ProgramDisplay> DISPLAY = Map.of(
            "CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT",
            new ProgramDisplay(
                    "My cashback is deposited into an eligible Tangerine "
                            + "Savings Account",
                    13,
                    Map.of(
                            "GROCERIES", "Groceries",
                            "GAS", "Gas",
                            "DINING", "Restaurants",
                            "TRANSIT", "Public Transportation and Parking"
                    )
            )
    );

    private final CardSelectionPolicyRepository policyRepository;
    private final RewardRuleRepository rewardRuleRepository;

    public SelectionPolicyViewService(
            CardSelectionPolicyRepository policyRepository,
            RewardRuleRepository rewardRuleRepository) {
        this.policyRepository = policyRepository;
        this.rewardRuleRepository = rewardRuleRepository;
    }

    public enum Status {
        /** The card has no customizable categories (e.g. RBC). */
        NO_POLICY,
        /** The policy is complete enough for the frontend to configure. */
        VALID,
        /** A policy exists but cannot be configured; the card is unusable. */
        INVALID
    }

    public record Evaluation(Status status, SelectionPolicyView view) {
    }

    // Callers must apply the catalogue filter first; this does not.
    public Optional<SelectionPolicyView> forCard(CreditCard card) {
        return Optional.ofNullable(evaluate(card).view());
    }

    public Evaluation evaluate(CreditCard card) {
        Optional<CardSelectionPolicy> found =
                policyRepository.findById(card.getId());
        if (found.isEmpty()) {
            return new Evaluation(Status.NO_POLICY, null);
        }
        CardSelectionPolicy policy = found.get();

        List<RewardRule> selectable = rewardRuleRepository
                .findByCreditCardId(card.getId())
                .stream()
                .filter(RewardRule::isSelectable)
                .sorted(Comparator.comparingInt(rule ->
                        SpendingCategories.ALL.indexOf(
                                rule.getSpendingCategory())))
                .toList();

        ProgramDisplay display = policy.getExtendedRequirement() == null
                ? null
                : DISPLAY.get(policy.getExtendedRequirement());

        String problem = findProblem(policy, selectable, display);
        if (problem != null) {
            log.warn("Card {} is not eligible for the catalogue: {}",
                    card.getId(), problem);
            return new Evaluation(Status.INVALID, null);
        }

        List<SelectableCategoryView> categories = selectable.stream()
                .map(rule -> new SelectableCategoryView(
                        rule.getSpendingCategory(),
                        display.categoryLabels()
                                .get(rule.getSpendingCategory()),
                        rule.getRewardRate(),
                        rule.getUnselectedRewardRate()))
                .toList();

        return new Evaluation(Status.VALID, new SelectionPolicyView(
                policy.getBaseSelectionLimit(),
                policy.getExtendedSelectionLimit(),
                policy.getExtendedRequirement(),
                display.requirementLabel(),
                policy.getChangeHoldDays(),
                categories.size(),
                display.offeredCategoryCount(),
                categories
        ));
    }

    // Returns the first reason the frontend could not configure this card.
    private String findProblem(
            CardSelectionPolicy policy,
            List<RewardRule> selectable,
            ProgramDisplay display) {

        if (selectable.isEmpty()) {
            return "the policy has no selectable categories";
        }
        if (display == null) {
            return "no display configuration for the policy requirement";
        }
        if (policy.getBaseSelectionLimit() > selectable.size()) {
            return "the base selection limit exceeds the selectable "
                    + "categories, so a selection could never be completed";
        }
        if (policy.getExtendedSelectionLimit()
                < policy.getBaseSelectionLimit()) {
            return "the extended selection limit is below the base limit";
        }
        if (display.offeredCategoryCount() < selectable.size()) {
            return "more categories are modelled than the issuer offers";
        }

        for (RewardRule rule : selectable) {
            String label = display.categoryLabels()
                    .get(rule.getSpendingCategory());
            if (label == null || label.isBlank()) {
                return "no display label for category "
                        + rule.getSpendingCategory();
            }

            BigDecimal selectedRate = rule.getRewardRate();
            BigDecimal unselectedRate = rule.getUnselectedRewardRate();
            if (selectedRate == null || unselectedRate == null
                    || unselectedRate.signum() < 0
                    || selectedRate.compareTo(unselectedRate) < 0) {
                return "inconsistent rates for category "
                        + rule.getSpendingCategory();
            }
        }

        return null;
    }
}
