
package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BreakEvenService {

    private static final BigDecimal TWELVE =
            BigDecimal.valueOf(12);

    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    private final CreditCardRepository creditCardRepository;
    private final RewardRuleRepository rewardRuleRepository;

    public BreakEvenService(
            CreditCardRepository creditCardRepository,
            RewardRuleRepository rewardRuleRepository
    ) {
        this.creditCardRepository = creditCardRepository;
        this.rewardRuleRepository = rewardRuleRepository;
    }

    public Map<String, Object> calculateBreakEven(
            Long cardAId,
            Long cardBId,
            Map<String, BigDecimal> monthlySpending
    ) {
        if (cardAId.equals(cardBId)) {
            throw new IllegalArgumentException(
                    "Please select two different cards."
            );
        }

        CreditCard cardA = creditCardRepository.findById(cardAId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Card A not found."
                        )
                );

        CreditCard cardB = creditCardRepository.findById(cardBId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Card B not found."
                        )
                );

        Map<String, BigDecimal> ratesA =
                getRewardRates(cardAId);

        Map<String, BigDecimal> ratesB =
                getRewardRates(cardBId);

        BigDecimal groceryRateA =
                getRequiredRate(ratesA, "GROCERIES");

        BigDecimal groceryRateB =
                getRequiredRate(ratesB, "GROCERIES");

        BigDecimal otherRewardsA =
                calculateOtherAnnualRewards(
                        ratesA, monthlySpending
                );

        BigDecimal otherRewardsB =
                calculateOtherAnnualRewards(
                        ratesB, monthlySpending
                );

        BigDecimal feeDifference =
                cardA.getAnnualFee()
                        .subtract(cardB.getAnnualFee());

        BigDecimal otherRewardDifference =
                otherRewardsA.subtract(otherRewardsB);

        BigDecimal numerator =
                feeDifference.subtract(otherRewardDifference);

        BigDecimal rateDifference =
                groceryRateA.subtract(groceryRateB)
                        .divide(HUNDRED, 10, RoundingMode.HALF_UP);

        BigDecimal denominator =
                TWELVE.multiply(rateDifference);

        Map<String, Object> result = new HashMap<>();

        result.put("cardA", cardA.getCardName());
        result.put("cardB", cardB.getCardName());

        BigDecimal currentGroceries =
                getRequiredSpending(
                        monthlySpending, "GROCERIES"
                );

        result.put(
                "currentMonthlyGroceries",
                currentGroceries
        );

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            result.put("breakEvenMonthlyGroceries", null);
            result.put("additionalMonthlyGroceries", null);
            result.put("status", "NO_CROSSOVER");

            BigDecimal netAAtZeroGroceries =
                    otherRewardsA.subtract(cardA.getAnnualFee());
            BigDecimal netBAtZeroGroceries =
                    otherRewardsB.subtract(cardB.getAnnualFee());
            int netComparison =
                    netAAtZeroGroceries.compareTo(netBAtZeroGroceries);

            String recommendation;
            if (netComparison > 0) {
                recommendation = cardA.getCardName() +
                        " is more profitable at every grocery spending " +
                        "level because both cards have the same grocery " +
                        "cashback rate.";
            } else if (netComparison < 0) {
                recommendation = cardB.getCardName() +
                        " is more profitable at every grocery spending " +
                        "level because both cards have the same grocery " +
                        "cashback rate.";
            } else {
                recommendation = "Both cards provide the same net rewards " +
                        "at every grocery spending level.";
            }

            result.put(
                    "recommendation",
                    recommendation
            );

            return result;
        }

        BigDecimal rawBreakEven =
                numerator.divide(
                        denominator,
                        10,
                        RoundingMode.HALF_UP
                );

        if (rawBreakEven.compareTo(BigDecimal.ZERO) < 0) {
            result.put("breakEvenMonthlyGroceries", null);
            result.put("additionalMonthlyGroceries", null);
            result.put("status", "NO_NONNEGATIVE_CROSSOVER");

            String consistentlyBetterCard =
                    rateDifference.signum() > 0
                            ? cardA.getCardName()
                            : cardB.getCardName();

            result.put(
                    "recommendation",
                    consistentlyBetterCard +
                    " is more profitable at all non-negative grocery " +
                    "spending levels; the cards' mathematical crossover " +
                    "is below $0/month."
            );

            return result;
        }

        BigDecimal breakEven =
                rawBreakEven.setScale(
                        2, RoundingMode.HALF_UP
                );

        BigDecimal additional =
                rawBreakEven.subtract(currentGroceries)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.UP);

        result.put(
                "breakEvenMonthlyGroceries",
                breakEven
        );

        result.put(
                "additionalMonthlyGroceries",
                additional
        );

        result.put("status", "BREAK_EVEN_FOUND");

        String recommendation = rateDifference.signum() > 0
                ? cardA.getCardName() +
                        " becomes more profitable above approximately $" +
                        breakEven + "/month in grocery spending."
                : cardA.getCardName() +
                        " is more profitable below approximately $" +
                        breakEven + "/month; " + cardB.getCardName() +
                        " is more profitable above it.";
        result.put(
                "recommendation",
                recommendation
        );

        return result;
    }

    private Map<String, BigDecimal> getRewardRates(
            Long cardId
    ) {
        List<RewardRule> rules =
                rewardRuleRepository.findByCreditCardId(cardId);

        Map<String, BigDecimal> rates = new HashMap<>();

        for (RewardRule rule : rules) {
            rates.put(
                    rule.getSpendingCategory().toUpperCase(),
                    rule.getRewardRate()
            );
        }

        return rates;
    }

    private BigDecimal calculateOtherAnnualRewards(
            Map<String, BigDecimal> rates,
            Map<String, BigDecimal> monthlySpending
    ) {
        BigDecimal total = BigDecimal.ZERO;

        for (String category : List.of(
                "GAS", "DINING", "TRAVEL", "OTHER"
        )) {
            BigDecimal spending =
                    getRequiredSpending(
                            monthlySpending, category
                    );

            BigDecimal rate =
                    getRequiredRate(rates, category);

            BigDecimal annualReward =
                    spending.multiply(TWELVE)
                            .multiply(rate)
                            .divide(
                                    HUNDRED,
                                    10,
                                    RoundingMode.HALF_UP
                            );

            total = total.add(annualReward);
        }

        return total;
    }

    private BigDecimal getRequiredRate(
            Map<String, BigDecimal> rates,
            String category
    ) {
        BigDecimal rate = rates.get(category);

        if (rate == null) {
            throw new IllegalArgumentException(
                    "Missing reward rule for " + category
            );
        }

        return rate;
    }

    private BigDecimal getRequiredSpending(
            Map<String, BigDecimal> spending,
            String category
    ) {
        BigDecimal amount = spending.get(category);

        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Invalid spending for " + category
            );
        }

        return amount;
    }
}
