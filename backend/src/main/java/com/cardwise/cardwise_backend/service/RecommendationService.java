package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CardSelectionPolicyRepository;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class RecommendationService {

    private final CreditCardRepository creditCardRepository;
    private final RewardRuleRepository rewardRuleRepository;
        private final RewardCalculationService rewardCalculationService;
        private final CardCatalogueEligibility cardCatalogueEligibility;
    private final CardSelectionPolicyRepository cardSelectionPolicyRepository;
    private final RewardRuleResolver rewardRuleResolver;

    public RecommendationService(
            CreditCardRepository creditCardRepository,
                        RewardRuleRepository rewardRuleRepository,
                        RewardCalculationService rewardCalculationService,
                        CardCatalogueEligibility cardCatalogueEligibility,
                        CardSelectionPolicyRepository cardSelectionPolicyRepository,
                        RewardRuleResolver rewardRuleResolver) {

        this.creditCardRepository = creditCardRepository;
        this.rewardRuleRepository = rewardRuleRepository;
                this.rewardCalculationService = rewardCalculationService;
                this.cardCatalogueEligibility = cardCatalogueEligibility;
        this.cardSelectionPolicyRepository = cardSelectionPolicyRepository;
        this.rewardRuleResolver = rewardRuleResolver;
    }

    public List<Map<String, Object>> recommend(
            Map<String, BigDecimal> monthlySpending) {
        return recommend(monthlySpending, Map.of());
    }

    public List<Map<String, Object>> recommend(
            Map<String, BigDecimal> monthlySpending,
            Map<Long, RewardSelection> selectionsByCardId) {

        Map<Long, RewardSelection> selections =
                selectionsByCardId == null ? Map.of() : selectionsByCardId;

        // Validate spending amounts
        for (String category : SpendingCategories.ALL) {
            BigDecimal amount = monthlySpending.get(category);

            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException(
                        "A non-negative amount is required for " + category
                );
            }
        }

        // Selections are looked up only for cards that pass the catalogue filter.
        return creditCardRepository.findAll()
                .stream()
                .filter(cardCatalogueEligibility::isEligible)
                .filter(card -> "CASHBACK".equals(card.getRewardType()))
                .map(card -> calculateReward(
                        card,
                        monthlySpending,
                        selections.getOrDefault(
                                card.getId(), RewardSelection.NONE)))
                .filter(result -> result != null)
                .sorted(Comparator.comparing(
                        result -> (BigDecimal) result.get("netAnnualReward"),
                        Comparator.reverseOrder()
                ))
                .toList();
    }

    Map<String, Object> calculateReward(
            CreditCard card,
            Map<String, BigDecimal> monthlySpending,
            RewardSelection selection) {

        List<RewardRule> rules =
                rewardRuleRepository.findByCreditCardId(card.getId());

        CardSelectionPolicy policy = cardSelectionPolicyRepository
                .findById(card.getId())
                .orElse(null);

        Map<String, EffectiveRewardRule> rulesByCategory =
                rewardRuleResolver.resolve(rules, policy, selection);

        // Exclude cards with incomplete reward rules
        if (!rulesByCategory.keySet()
                .containsAll(SpendingCategories.ALL)) {
            return null;
        }

        BigDecimal annualReward = BigDecimal.ZERO;
        Map<String, BigDecimal> rewardBreakdown = new HashMap<>();

        for (String category : SpendingCategories.ALL) {

            BigDecimal monthlyAmount = monthlySpending.get(category);
            EffectiveRewardRule rule = rulesByCategory.get(category);

            BigDecimal categoryReward;
            categoryReward = rewardCalculationService.calculateAnnualReward(
                    rule,
                    monthlyAmount
            );

            annualReward = annualReward.add(categoryReward);
            rewardBreakdown.put(category, categoryReward);
        }

        BigDecimal netAnnualReward =
                annualReward.subtract(card.getAnnualFee());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cardId", card.getId());
        result.put("cardName", card.getCardName());
        result.put("annualFee", card.getAnnualFee());
        result.put("annualReward", annualReward);
        result.put("netAnnualReward", netAnnualReward);
        result.put("rewardBreakdown", rewardBreakdown);

        // Describes the selection supplied, not the cardholder's account settings.
        if (policy != null) {
            int selectedCount = selection.categories().size();
            boolean complete =
                    selectedCount >= policy.getBaseSelectionLimit();
            String mode = selectedCount == 0
                    ? "NONE_SELECTED"
                    : complete ? "SELECTED" : "PARTIAL_SELECTION";

            result.put("selectionMode", mode);
            result.put("selectionRequired", !complete);
            result.put(
                    "selectedCategories",
                    List.copyOf(new TreeSet<>(selection.categories())));
        }

        return result;
    }
}