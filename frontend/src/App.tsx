
import { useState } from "react";
import WhatIfSimulator from "./components/WhatIfSimulator";
import {
  BACKEND_SPENDING_CATEGORIES,
  type Spending,
} from "./types/Spending";
import "./App.css";

type Recommendation = {
  cardId: number;
  cardName: string;
  annualFee: number;
  annualReward: number;
  netAnnualReward: number;
  rewardBreakdown?: Record<string, number>;
};

type BreakEvenResult = {
  cardA: string;
  cardB: string;
  currentMonthlyGroceries: number;
  breakEvenMonthlyGroceries: number | null;
  additionalMonthlyGroceries: number | null;
  status: string;
  recommendation: string;
};

const categories = BACKEND_SPENDING_CATEGORIES;

function generateExplanation(
  card: Recommendation,
  bestCard: Recommendation,
  recommendations: Recommendation[]
): string {
  const formatMoney = (value: number) =>
    new Intl.NumberFormat("en-CA", {
      style: "currency",
      currency: "CAD",
    }).format(value);

  const secondCard = recommendations.find(
    (item) => item.cardId !== bestCard.cardId
  );

  // Explain why the winning card is better.
  if (card.cardId === bestCard.cardId && secondCard) {
    const difference =
      bestCard.netAnnualReward - secondCard.netAnnualReward;

    const bestBreakdown = bestCard.rewardBreakdown ?? {};
    const secondBreakdown = secondCard.rewardBreakdown ?? {};

    // Calculate the cashback advantage for each category.
    const categoryDifferences = Object.keys(bestBreakdown)
      .map((category) => ({
        category,
        difference:
          (bestBreakdown[category] ?? 0) -
          (secondBreakdown[category] ?? 0),
      }))
      .sort((a, b) => b.difference - a.difference);

    const strongestCategory = categoryDifferences.find(
      (item) => item.difference > 0
    );

    const categoryExplanation = strongestCategory
      ? `The biggest cashback advantage comes from ${
          strongestCategory.category.toLowerCase()
        }, where this card earns ${
          formatMoney(strongestCategory.difference)
        } more annually.`
      : "Its overall rewards and annual fee make it the better choice.";

    const feeExplanation =
      bestCard.annualFee > secondCard.annualFee
        ? `Even after accounting for its higher annual fee, `
        : `After accounting for annual fees, `;

    return (
      `${bestCard.cardName} is your best choice. ` +
      categoryExplanation +
      ` ${feeExplanation}it provides ` +
      `${formatMoney(difference)} more net rewards per year ` +
      `than ${secondCard.cardName}.`
    );
  }

  // Explain why a lower-ranked card is not the best.
  if (card.cardId !== bestCard.cardId) {
    const difference =
      bestCard.netAnnualReward - card.netAnnualReward;

    return (
      `${card.cardName} earns ${formatMoney(
        card.netAnnualReward
      )} in net annual rewards, which is ` +
      `${formatMoney(difference)} less than ` +
      `${bestCard.cardName} for your spending.`
    );
  }

  // Only one card is available.
  return (
    `${card.cardName} provides ${formatMoney(
      card.netAnnualReward
    )} in net annual rewards after fees.`
  );
}


const formatMoney = (value: number) =>
  new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
  }).format(value);

export default function App() {
  const [spending, setSpending] = useState<Spending>({
    groceries: 600,
    gas: 200,
    dining: 300,
    travel: 100,
    other: 400,
    transit: 0,
    rideshare: 0,
    evCharging: 0,
  });

  const [recommendations, setRecommendations] =
    useState<Recommendation[]>([]);
  const [breakEven, setBreakEven] =
    useState<BreakEvenResult | null>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const updateSpending = (
    category: keyof Spending,
    value: number
  ) => {
    setSpending((previous) => ({
      ...previous,
      [category]: value,
    }));

    // Hide old results when spending changes.
    setRecommendations([]);
    setBreakEven(null);
  };

  const findBestCard = async () => {
    if (
      Object.values(spending).some(
        (amount) => !Number.isFinite(amount) || amount < 0
      )
    ) {
      setError("Please enter valid non-negative amounts.");
      return;
    }

    setLoading(true);
    setError("");
    setRecommendations([]);
    setBreakEven(null);

    try {
      const params = new URLSearchParams(
        Object.entries(spending).map(([key, value]) => [
          key,
          String(value),
        ])
      );

      const response = await fetch(
        `/api/v1/recommendations?${params.toString()}`
      );

      if (!response.ok) {
        throw new Error("Unable to fetch recommendations.");
      }

      const data: Recommendation[] = await response.json();
      setRecommendations(data);

      const groceryCard = data.find(
        (card) => card.cardName === "Grocery Rewards Plus"
      );

      const everydayCard = data.find(
        (card) => card.cardName === "Everyday Cashback"
      );

      if (groceryCard && everydayCard) {
        const breakEvenParams = new URLSearchParams({
          cardAId: String(groceryCard.cardId),
          cardBId: String(everydayCard.cardId),
          groceries: String(spending.groceries),
          gas: String(spending.gas),
          dining: String(spending.dining),
          travel: String(spending.travel),
          other: String(spending.other),
          transit: String(spending.transit),
          rideshare: String(spending.rideshare),
          evCharging: String(spending.evCharging),
        });

        try {
          const breakEvenResponse = await fetch(
            `/api/v1/break-even?${breakEvenParams.toString()}`
          );

          if (!breakEvenResponse.ok) {
            throw new Error("Break-even API request failed");
          }

          const result: BreakEvenResult =
            await breakEvenResponse.json();

          setBreakEven(result);
        } catch (breakEvenError) {
          console.error(
            "Unable to load break-even analysis:",
            breakEvenError
          );
        }
      }
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Something went wrong."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="app">
      <h1>CardWise AI</h1>
      <p>Find the best credit card for your spending habits.</p>

      <h2>Your Monthly Spending</h2>

      {Object.entries(spending).map(([category, amount]) => (
        <div className="spending-field" key={category}>
          <label htmlFor={category}>
            {category === "evCharging"
              ? "EV Charging"
              : category.charAt(0).toUpperCase() + category.slice(1)}
          </label>

          <input
            id={category}
            type="number"
            min="0"
            step="0.01"
            value={amount}
            onChange={(event) =>
              updateSpending(
                category as keyof Spending,
                Number(event.target.value)
              )
            }
          />
        </div>
      ))}

      <button
        type="button"
        onClick={findBestCard}
        disabled={loading}
      >
        {loading
          ? "Finding Best Card..."
          : "Find My Best Credit Card"}
      </button>

      {error && <p className="error">{error}</p>}

      {breakEven && (
        <section className="break-even-card">
          <h2>Grocery Spending Break-Even Analysis</h2>

          {breakEven.status === "BREAK_EVEN_FOUND" &&
          breakEven.breakEvenMonthlyGroceries !== null ? (
            <>
              <p>
                Grocery Rewards Plus becomes more profitable
                than Everyday Cashback above:
              </p>

              <div className="break-even-amount">
                {formatMoney(
                  breakEven.breakEvenMonthlyGroceries
                )}
                <span> / month</span>
              </div>

              <p>
                Your current grocery spending:{" "}
                <strong>
                  {formatMoney(
                    breakEven.currentMonthlyGroceries
                  )}
                </strong>
              </p>

              {breakEven.additionalMonthlyGroceries !== null &&
              breakEven.additionalMonthlyGroceries > 0 ? (
                <p>
                  You need approximately{" "}
                  <strong>
                    {formatMoney(
                      breakEven.additionalMonthlyGroceries
                    )}
                  </strong>{" "}
                  more in monthly grocery spending to reach
                  the break-even point.
                </p>
              ) : (
                <p>
                  Your grocery spending is already at or above
                  the break-even point.
                </p>
              )}
            </>
          ) : (
            <p>{breakEven.recommendation}</p>
          )}

          <p className="break-even-note">
            Assumes other spending stays unchanged and
            cashback rates remain constant.
          </p>
        </section>
      )}

      {recommendations.length > 0 && (
        <section className="results">
          <h2>Your Recommended Credit Cards</h2>

          {recommendations.map((card, index) => (
            <article
              className="recommendation-card"
              key={card.cardId}
            >
              <div className="card-heading">
                <h3>
                  {index + 1}. {card.cardName}
                </h3>

                {index === 0 && (
                  <span className="best-badge">
                    Best Match
                  </span>
                )}
              </div>

              <div className="reward-summary">
                <div>
                  <span>Annual Cashback</span>
                  <strong>
                    {formatMoney(card.annualReward)}
                  </strong>
                </div>

                <div>
                  <span>Annual Fee</span>
                  <strong>
                    {formatMoney(card.annualFee)}
                  </strong>
                </div>

                <div className="net-reward">
                  <span>Net Annual Reward</span>
                  <strong>
                    {formatMoney(card.netAnnualReward)}
                  </strong>
                </div>
              </div>


              <div className="recommendation-explanation">
                <h4>Why this card?</h4>
                <p>
                  {generateExplanation(card, recommendations[0], recommendations)}
                </p>
              </div>

              <h4>Cashback Breakdown</h4>

              {card.rewardBreakdown ? (
                <div className="breakdown">
                  {categories.map((category) => {
                    const reward =
                      card.rewardBreakdown?.[category] ?? 0;

                    const percentage =
                      card.annualReward > 0
                        ? (reward / card.annualReward) * 100
                        : 0;

                    return (
                      <div
                        className="breakdown-item"
                        key={category}
                      >
                        <div className="breakdown-label">
                          <span>
                            {category === "EV_CHARGING"
                              ? "EV Charging"
                              : category.charAt(0) +
                                category.slice(1).toLowerCase()}
                          </span>
                          <strong>
                            {formatMoney(reward)}
                          </strong>
                        </div>

                        <div
                          className="progress-track"
                          role="progressbar"
                          aria-label={`${category} share of cashback`}
                          aria-valuenow={Math.round(percentage)}
                          aria-valuemin={0}
                          aria-valuemax={100}
                        >
                          <div
                            className="progress-fill"
                            style={{
                              width: `${percentage}%`,
                            }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <p className="breakdown-unavailable">
                  Category breakdown is not available.
                  Check that your backend returns
                  rewardBreakdown.
                </p>
              )}
            </article>
          ))}

          <p className="disclaimer">
            Estimates use fictional credit cards and simplified
            reward rules for development.
          </p>
        </section>
      )}

      {recommendations.length > 0 && (
        <WhatIfSimulator
          spending={spending}
          breakEvenMonthlyGroceries={
            breakEven?.status === "BREAK_EVEN_FOUND"
              ? breakEven.breakEvenMonthlyGroceries
              : null
          }
        />
      )}
    </main>
  );
}
