package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.entity.RewardRule;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.repository.RewardRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BreakEvenService {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int PRECISION = 12;

    private static final List<String> OTHER_CATEGORIES =
            List.of("GAS", "DINING", "TRAVEL", "OTHER");

    private final CreditCardRepository creditCardRepository;
    private final RewardRuleRepository rewardRuleRepository;
    private final RewardCalculationService rewardCalculationService;

    public BreakEvenService(
            CreditCardRepository creditCardRepository,
            RewardRuleRepository rewardRuleRepository,
            RewardCalculationService rewardCalculationService
    ) {
        this.creditCardRepository = creditCardRepository;
        this.rewardRuleRepository = rewardRuleRepository;
        this.rewardCalculationService = rewardCalculationService;
    }

    public Map<String, Object> calculateBreakEven(
            Long cardAId,
            Long cardBId,
            Map<String, BigDecimal> monthlySpending
    ) {
        if (cardAId == null || cardBId == null || cardAId.equals(cardBId)) {
            throw new IllegalArgumentException(
                    "Please select two different cards."
            );
        }

        BigDecimal currentGroceries =
                getRequiredSpending(monthlySpending, "GROCERIES");

        CreditCard cardA = creditCardRepository.findById(cardAId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Card A not found.")
                );

        CreditCard cardB = creditCardRepository.findById(cardBId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Card B not found.")
                );

        Map<String, RewardRule> rulesA = getRules(cardAId);
        Map<String, RewardRule> rulesB = getRules(cardBId);

        RewardRule groceryA = getRequiredRule(rulesA, "GROCERIES");
        RewardRule groceryB = getRequiredRule(rulesB, "GROCERIES");

        BigDecimal fixedNetA = calculateOtherAnnualRewards(
                rulesA, monthlySpending
        ).subtract(cardA.getAnnualFee());
        BigDecimal fixedNetB = calculateOtherAnnualRewards(
                rulesB, monthlySpending
        ).subtract(cardB.getAnnualFee());

        // Boundaries divide reward curves into intervals with constant rates.
        TreeSet<BigDecimal> boundaries = new TreeSet<>();
        boundaries.add(BigDecimal.ZERO);
        addCapBoundary(boundaries, groceryA);
        addCapBoundary(boundaries, groceryB);

        List<BigDecimal> intervals = new ArrayList<>(boundaries);
        TreeSet<BigDecimal> crossoverPoints = new TreeSet<>();
        boolean sameSlopeEverywhere = true;

        // Solve each bounded interval.
        for (int i = 0; i < intervals.size() - 1; i++) {
            BigDecimal left = intervals.get(i);
            BigDecimal right = intervals.get(i + 1);

            BigDecimal leftDifference = difference(
                    groceryA, groceryB, fixedNetA, fixedNetB, left
            );
            BigDecimal rightDifference = difference(
                    groceryA, groceryB, fixedNetA, fixedNetB, right
            );

                        if (leftDifference.compareTo(rightDifference) != 0) {
                                sameSlopeEverywhere = false;
            }

            addRootWithinInterval(
                    crossoverPoints,
                    left,
                    right,
                    leftDifference,
                    rightDifference
            );
        }

        // Solve the final unbounded interval.
        BigDecimal lastBoundary = intervals.get(intervals.size() - 1);
        BigDecimal lastDifference = difference(
                groceryA, groceryB, fixedNetA, fixedNetB, lastBoundary
        );
        BigDecimal nextDifference = difference(
                groceryA,
                groceryB,
                fixedNetA,
                fixedNetB,
                lastBoundary.add(BigDecimal.ONE)
        );

                if (lastDifference.compareTo(nextDifference) != 0) {
                        sameSlopeEverywhere = false;
        }

        addRootOnFinalInterval(
                crossoverPoints,
                lastBoundary,
                lastDifference,
                nextDifference
        );

        List<BigDecimal> roundedPoints = crossoverPoints.stream()
                .map(point -> point.setScale(2, RoundingMode.HALF_UP))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("cardA", cardA.getCardName());
        result.put("cardB", cardB.getCardName());
        result.put("currentMonthlyGroceries", currentGroceries);
        result.put("breakEvenPoints", roundedPoints);

        if (sameSlopeEverywhere) {
            result.put("status", "NO_CROSSOVER");
            result.put("breakEvenMonthlyGroceries", null);
            result.put("additionalMonthlyGroceries", null);

            BigDecimal differenceAtZero = difference(
                    groceryA,
                    groceryB,
                    fixedNetA,
                    fixedNetB,
                    BigDecimal.ZERO
            );
            String recommendation;
            if (differenceAtZero.signum() > 0) {
                recommendation = cardA.getCardName() +
                        " is more profitable at every grocery spending " +
                        "level because both cards have the same grocery " +
                        "reward curve.";
            } else if (differenceAtZero.signum() < 0) {
                recommendation = cardB.getCardName() +
                        " is more profitable at every grocery spending " +
                        "level because both cards have the same grocery " +
                        "reward curve.";
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

        if (roundedPoints.isEmpty()) {
            result.put("status", "NO_NONNEGATIVE_CROSSOVER");
            result.put("breakEvenMonthlyGroceries", null);
            result.put("additionalMonthlyGroceries", null);

            BigDecimal atZero = difference(
                    groceryA,
                    groceryB,
                    fixedNetA,
                    fixedNetB,
                    BigDecimal.ZERO
            );
            String better = atZero.signum() >= 0
                    ? cardA.getCardName()
                    : cardB.getCardName();

            result.put(
                    "recommendation",
                    better + " provides higher net rewards across " +
                            "non-negative grocery spending levels."
            );
            return result;
        }

        BigDecimal first = roundedPoints.get(0);
        BigDecimal nextAboveCurrent = roundedPoints.stream()
                .filter(point -> point.compareTo(currentGroceries) > 0)
                .findFirst()
                .orElse(null);
        BigDecimal additional = nextAboveCurrent == null
                ? BigDecimal.ZERO.setScale(2)
                : nextAboveCurrent.subtract(currentGroceries)
                        .setScale(2, RoundingMode.UP);

        result.put("status", roundedPoints.size() > 1
                ? "MULTIPLE_CROSSOVERS"
                : "BREAK_EVEN_FOUND");
        result.put("breakEvenMonthlyGroceries", first);
        result.put("additionalMonthlyGroceries", additional);

        String recommendation;
        if (roundedPoints.size() > 1) {
            recommendation = "The cards have multiple crossover points at " +
                    roundedPoints + " per month in grocery spending.";
        } else {
            BigDecimal probe = new BigDecimal("0.01");
            BigDecimal belowGroceries = first.subtract(probe)
                    .max(BigDecimal.ZERO);
            BigDecimal aboveGroceries = first.add(probe);
            BigDecimal belowDifference = difference(
                    groceryA,
                    groceryB,
                    fixedNetA,
                    fixedNetB,
                    belowGroceries
            );
            BigDecimal aboveDifference = difference(
                    groceryA,
                    groceryB,
                    fixedNetA,
                    fixedNetB,
                    aboveGroceries
            );

            if (belowDifference.signum() >= 0 &&
                    aboveDifference.signum() <= 0) {
                recommendation = cardA.getCardName() +
                        " is more profitable below approximately $" +
                        first + "/month; " + cardB.getCardName() +
                        " is more profitable above it.";
            } else {
                recommendation = cardB.getCardName() +
                        " is more profitable below approximately $" +
                        first + "/month; " + cardA.getCardName() +
                        " is more profitable above it.";
            }
        }
        result.put("recommendation", recommendation);

        return result;
    }

    private Map<String, RewardRule> getRules(Long cardId) {
        return rewardRuleRepository.findByCreditCardId(cardId)
                .stream()
                .collect(Collectors.toMap(
                        rule -> rule.getSpendingCategory().toUpperCase(),
                        Function.identity()
                ));
    }

    private BigDecimal calculateOtherAnnualRewards(
            Map<String, RewardRule> rules,
            Map<String, BigDecimal> monthlySpending
    ) {
        BigDecimal total = BigDecimal.ZERO;

        for (String category : OTHER_CATEGORIES) {
            BigDecimal monthlyAmount =
                    getRequiredSpending(monthlySpending, category);
            RewardRule rule = getRequiredRule(rules, category);
            total = total.add(
                    rewardCalculationService.calculateAnnualReward(
                            rule,
                            monthlyAmount
                    )
            );
        }

        return total;
    }

    private BigDecimal difference(
            RewardRule groceryA,
            RewardRule groceryB,
            BigDecimal fixedNetA,
            BigDecimal fixedNetB,
            BigDecimal monthlyGroceries
    ) {
        BigDecimal netA = fixedNetA.add(
                rewardCalculationService.calculateAnnualReward(
                        groceryA,
                        monthlyGroceries
                )
        );
        BigDecimal netB = fixedNetB.add(
                rewardCalculationService.calculateAnnualReward(
                        groceryB,
                        monthlyGroceries
                )
        );

        return netA.subtract(netB);
    }

    private void addCapBoundary(
            TreeSet<BigDecimal> boundaries,
            RewardRule rule
    ) {
        if (rule.getSpendingCap() == null) {
            return;
        }

        BigDecimal monthlyBoundary;
        if ("MONTHLY".equals(rule.getCapPeriod())) {
            monthlyBoundary = rule.getSpendingCap();
        } else if ("ANNUAL".equals(rule.getCapPeriod())) {
            monthlyBoundary = rule.getSpendingCap().divide(
                    TWELVE,
                    PRECISION,
                    RoundingMode.HALF_UP
            );
        } else {
            throw new IllegalArgumentException(
                    "Unsupported cap period: " + rule.getCapPeriod()
            );
        }

        if (monthlyBoundary.compareTo(BigDecimal.ZERO) > 0) {
            boundaries.add(monthlyBoundary);
        }
    }

    private void addRootWithinInterval(
            TreeSet<BigDecimal> roots,
            BigDecimal left,
            BigDecimal right,
            BigDecimal leftDifference,
            BigDecimal rightDifference
    ) {
        if (leftDifference.signum() == 0) {
            roots.add(left);
        }
        if (rightDifference.signum() == 0) {
            roots.add(right);
        }

        if (leftDifference.signum() == 0 ||
                rightDifference.signum() == 0 ||
                leftDifference.signum() == rightDifference.signum()) {
            return;
        }

        BigDecimal slope = rightDifference.subtract(leftDifference)
                .divide(right.subtract(left), PRECISION, RoundingMode.HALF_UP);
        if (slope.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal root = left.subtract(
                leftDifference.divide(slope, PRECISION, RoundingMode.HALF_UP)
        );

        if (root.compareTo(left) >= 0 && root.compareTo(right) <= 0) {
            roots.add(root);
        }
    }

    private void addRootOnFinalInterval(
            TreeSet<BigDecimal> roots,
            BigDecimal boundary,
            BigDecimal boundaryDifference,
            BigDecimal nextDifference
    ) {
        if (boundaryDifference.signum() == 0) {
            roots.add(boundary);
        }

                if (boundaryDifference.signum() == 0) {
            return;
        }

        BigDecimal slope = nextDifference.subtract(boundaryDifference);
        if (slope.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal root = boundary.subtract(
                boundaryDifference.divide(slope, PRECISION, RoundingMode.HALF_UP)
        );

        if (root.compareTo(boundary) >= 0) {
            roots.add(root);
        }
    }

    private BigDecimal getRequiredSpending(
            Map<String, BigDecimal> spending,
            String category
    ) {
        BigDecimal amount = spending == null ? null : spending.get(category);
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Invalid spending for " + category
            );
        }
        return amount;
    }

    private RewardRule getRequiredRule(
            Map<String, RewardRule> rules,
            String category
    ) {
        RewardRule rule = rules.get(category);
        if (rule == null) {
            throw new IllegalArgumentException(
                    "Missing reward rule for " + category
            );
        }
        return rule;
    }
}
