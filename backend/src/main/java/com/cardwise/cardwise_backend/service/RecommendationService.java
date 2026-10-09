package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    private static final List<String> CATEGORIES = List.of(
            "GROCERIES",
            "GAS",
            "DINING",
            "TRAVEL",
            "OTHER"
    );

    private final CreditCardRepository creditCardRepository;
    private final RewardRuleRepository rewardRuleRepository;

    public RecommendationService(
            CreditCardRepository creditCardRepository,
            RewardRuleRepository rewardRuleRepository) {

        this.creditCardRepository = creditCardRepository;
        this.rewardRuleRepository = rewardRuleRepository;
    }

    public List<Map<String, Object>> recommend(
            Map<String, BigDecimal> monthlySpending) {

        // Validate spending amounts
        for (String category : CATEGORIES) {
            BigDecimal amount = monthlySpending.get(category);

            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException(
                        "A non-negative amount is required for " + category
                );
            }
        }

        return creditCardRepository.findAll()
                .stream()
                .filter(card -> "CASHBACK".equals(card.getRewardType()))
                .map(card -> calculateReward(card, monthlySpending))
                .filter(result -> result != null)
                .sorted(Comparator.comparing(
                        result -> (BigDecimal) result.get("netAnnualReward"),
                        Comparator.reverseOrder()
                ))
                .toList();
    }

    private Map<String, Object> calculateReward(
            CreditCard card,
            Map<String, BigDecimal> monthlySpending) {

        List<RewardRule> rules =
                rewardRuleRepository.findByCreditCardId(card.getId());

        Map<String, RewardRule> rulesByCategory = new HashMap<>();
        for (RewardRule rule : rules) {
            rulesByCategory.put(rule.getSpendingCategory(), rule);
        }

        // Exclude cards with incomplete reward rules
        if (!rulesByCategory.keySet().containsAll(CATEGORIES)) {
            return null;
        }

        BigDecimal annualReward = BigDecimal.ZERO;
        Map<String, BigDecimal> rewardBreakdown = new HashMap<>();

        for (String category : CATEGORIES) {

            BigDecimal monthlyAmount = monthlySpending.get(category);
            RewardRule rule = rulesByCategory.get(category);

            BigDecimal annualSpending =
                    monthlyAmount.multiply(BigDecimal.valueOf(12));

            BigDecimal categoryReward;

            if (rule.getSpendingCap() == null) {
                // Existing behavior for uncapped cards.
                categoryReward = annualSpending
                        .multiply(rule.getRewardRate())
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        );
            } else {
                BigDecimal annualCap;

                if ("MONTHLY".equals(rule.getCapPeriod())) {
                    annualCap = rule.getSpendingCap()
                            .multiply(BigDecimal.valueOf(12));
                } else if ("ANNUAL".equals(rule.getCapPeriod())) {
                    annualCap = rule.getSpendingCap();
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported cap period: " +
                                    rule.getCapPeriod()
                    );
                }

                if (rule.getBaseRewardRate() == null) {
                    throw new IllegalArgumentException(
                            "Fallback reward rate is required for capped rules"
                    );
                }

                BigDecimal eligibleSpending =
                        annualSpending.min(annualCap);
                BigDecimal excessSpending =
                        annualSpending.subtract(eligibleSpending);

                BigDecimal cappedReward = eligibleSpending
                        .multiply(rule.getRewardRate());
                BigDecimal fallbackReward = excessSpending
                        .multiply(rule.getBaseRewardRate());

                categoryReward = cappedReward
                        .add(fallbackReward)
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        );
            }

            annualReward = annualReward.add(categoryReward);
            rewardBreakdown.put(category, categoryReward);
        }

        BigDecimal netAnnualReward =
                annualReward.subtract(card.getAnnualFee());

        return Map.of(
        "cardId", card.getId(),
        "cardName", card.getCardName(),
        "annualFee", card.getAnnualFee(),
        "annualReward", annualReward,
        "netAnnualReward", netAnnualReward,
        "rewardBreakdown", rewardBreakdown
);
    }
}