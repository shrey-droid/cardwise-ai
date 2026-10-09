
import { lazy, Suspense, useEffect, useState } from "react";
import "./WhatIfSimulator.css";
import type { Spending } from "../types/Spending";

const CashbackComparisonChart = lazy(
  () => import("./CashbackComparisonChart")
);

type Recommendation = {
  cardId: number;
  cardName: string;
  annualFee: number;
  annualReward: number;
  netAnnualReward: number;
};

type CreditCardOption = {
  id: number;
  cardName: string;
  rewardType: string;
};

type Props = {
  spending: Spending;
  breakEvenMonthlyGroceries: number | null;
};

const formatMoney = (amount: number) =>
  new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
  }).format(amount);

export default function WhatIfSimulator({
  spending,
  breakEvenMonthlyGroceries,
}: Props) {
  const [groceries, setGroceries] = useState(spending.groceries);
  const [recommendations, setRecommendations] =
    useState<Recommendation[]>([]);
  const [availableCards, setAvailableCards] =
    useState<CreditCardOption[]>([]);
  const [cardsLoading, setCardsLoading] = useState(true);
  const [cardsError, setCardsError] = useState("");
  const [cardAId, setCardAId] = useState<number | null>(null);
  const [cardBId, setCardBId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const sliderMax = Math.max(
    1000,
    spending.groceries,
    breakEvenMonthlyGroceries ?? 0
  );

  const breakEvenPosition =
    breakEvenMonthlyGroceries !== null
      ? Math.min(
          100,
          Math.max(
            0,
            (breakEvenMonthlyGroceries / sliderMax) * 100
          )
        )
      : null;

  useEffect(() => {
    const controller = new AbortController();

    // Debounce API calls when the slider moves.
    const timeout = setTimeout(async () => {
      setLoading(true);
      setError("");

      const params = new URLSearchParams({
        groceries: String(groceries),
        gas: String(spending.gas),
        dining: String(spending.dining),
        travel: String(spending.travel),
        other: String(spending.other),
        transit: String(spending.transit),
        rideshare: String(spending.rideshare),
        evCharging: String(spending.evCharging),
      });

      try {
        const response = await fetch(
          `/api/v1/recommendations?${params.toString()}`,
          { signal: controller.signal }
        );

        if (!response.ok) {
          throw new Error("Unable to calculate simulated rewards.");
        }

        const data: Recommendation[] = await response.json();

        if (!controller.signal.aborted) {
          setRecommendations(data);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(
            err instanceof Error
              ? err.message
              : "Simulation failed."
          );
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }, 300);

    return () => {
      clearTimeout(timeout);
      controller.abort();
    };
  }, [
    groceries,
    spending.gas,
    spending.dining,
    spending.travel,
    spending.other,
    spending.transit,
    spending.rideshare,
    spending.evCharging,
  ]);

  useEffect(() => {
    const controller = new AbortController();

    async function loadCards() {
      try {
        const response = await fetch(
          "/api/v1/cards?rewardType=CASHBACK",
          { signal: controller.signal }
        );

        if (!response.ok) {
          throw new Error("Unable to load cashback cards.");
        }

        const cards: CreditCardOption[] =
          await response.json();

        if (!controller.signal.aborted) {
          setAvailableCards(cards);
          setCardsError("");

          const selectedA = cards[0]?.id ?? null;
          const selectedB =
            cards.find((card) => card.id !== selectedA)?.id ?? null;

          setCardAId((current) =>
            current !== null &&
            cards.some((card) => card.id === current)
              ? current
              : selectedA
          );

          setCardBId((current) =>
            current !== null &&
            current !== selectedA &&
            cards.some((card) => card.id === current)
              ? current
              : selectedB
          );
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setCardsError(
            err instanceof Error
              ? err.message
              : "Unable to load cards."
          );
        }
      } finally {
        if (!controller.signal.aborted) {
          setCardsLoading(false);
        }
      }
    }

    // Defer by one task so React StrictMode's development-only
    // setup/cleanup pass can cancel before starting a duplicate request.
    const timeout = window.setTimeout(() => {
      void loadCards();
    }, 0);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, []);

  const bestCard = recommendations[0];
  const secondCard = recommendations[1];

  const difference =
    bestCard && secondCard
      ? bestCard.netAnnualReward - secondCard.netAnnualReward
      : 0;

  const maxReward = Math.max(
    1,
    ...recommendations.map((card) =>
      Math.max(0, card.netAnnualReward)
    )
  );

  return (
    <section className="simulator">
      <h2>What-If Spending Simulator</h2>

      <p>
        Adjust your monthly grocery spending and compare
        the net annual rewards for each credit card.
      </p>

      <div className="card-selection">
        <div className="card-selection-field">
          <label htmlFor="card-a">First Credit Card</label>

          <select
            id="card-a"
            value={cardAId ?? ""}
            disabled={cardsLoading || availableCards.length < 2}
            onChange={(event) =>
              setCardAId(Number(event.target.value))
            }
          >
            {availableCards.map((card) => (
              <option key={card.id} value={card.id}>
                {card.cardName}
              </option>
            ))}
          </select>
        </div>

        <div className="card-selection-field">
          <label htmlFor="card-b">Second Credit Card</label>

          <select
            id="card-b"
            value={cardBId ?? ""}
            disabled={cardsLoading || availableCards.length < 2}
            onChange={(event) =>
              setCardBId(Number(event.target.value))
            }
          >
            {availableCards.map((card) => (
              <option key={card.id} value={card.id}>
                {card.cardName}
              </option>
            ))}
          </select>
        </div>
      </div>

      {cardsLoading && <p>Loading cashback cards...</p>}

      {cardsError && (
        <p className="error">{cardsError}</p>
      )}

      {!cardsLoading &&
        !cardsError &&
        availableCards.length < 2 && (
          <p>
            At least two cashback cards are required
            for comparison.
          </p>
        )}

      {cardAId !== null &&
        cardBId !== null &&
        cardAId === cardBId && (
        <p className="error">
          Please select two different credit cards.
        </p>
      )}

      <div className="simulator-slider-header">
        <label htmlFor="grocery-slider">
          Monthly Grocery Spending
        </label>

        <strong>{formatMoney(groceries)}</strong>
      </div>

      <div className="simulator-slider-container">
        <input
          id="grocery-slider"
          type="range"
          min="0"
          max={sliderMax}
          step="1"
          value={groceries}
          onChange={(event) =>
            setGroceries(Number(event.target.value))
          }
        />

        {breakEvenPosition !== null && (
          <div
            className="break-even-marker"
            style={{ left: `${breakEvenPosition}%` }}
            title={
              breakEvenMonthlyGroceries !== null
                ? `Break-even: ${formatMoney(
                    breakEvenMonthlyGroceries
                  )}`
                : undefined
            }
            aria-hidden="true"
          >
            <span className="break-even-marker-line" />
          </div>
        )}
      </div>

      <div className="simulator-scale">
        <span>$0</span>
        <span>{formatMoney(sliderMax)}</span>
      </div>

      {breakEvenMonthlyGroceries !== null && (
        <p className="simulator-threshold">
          Break-even grocery spending:{" "}
          <strong>
            {formatMoney(breakEvenMonthlyGroceries)}/month
          </strong>
        </p>
      )}

      {loading && (
        <p className="simulator-loading">
          Updating recommendations...
        </p>
      )}

      {error && <p className="error">{error}</p>}

      {recommendations.length > 0 && (
        <>
          <div className="simulator-results">
            {recommendations.map((card, index) => (
              <div
                className="simulator-result"
                key={card.cardId}
              >
                <div className="simulator-card-header">
                  <span>{card.cardName}</span>

                  {index === 0 && (
                    <span>Best Match</span>
                  )}
                </div>

                <strong>
                  {formatMoney(card.netAnnualReward)}
                </strong>

                <span className="simulator-caption">
                  Net annual reward
                </span>
              </div>
            ))}
          </div>

          <div className="simulator-chart">
            <h3>Annual Reward Comparison</h3>

            {recommendations.map((card) => {
              const width =
                (Math.max(0, card.netAnnualReward) /
                  maxReward) *
                100;

              return (
                <div
                  className="simulator-chart-row"
                  key={card.cardId}
                >
                  <div className="simulator-chart-label">
                    <span>{card.cardName}</span>
                    <strong>
                      {formatMoney(card.netAnnualReward)}
                    </strong>
                  </div>

                  <div className="simulator-chart-track">
                    <div
                      className="simulator-chart-fill"
                      style={{ width: `${width}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>

          {cardAId !== null &&
            cardBId !== null &&
            cardAId !== cardBId && (
            <Suspense
              fallback={
                <p className="simulator-chart-note">
                  Loading detailed comparison chart...
                </p>
              }
            >
              <CashbackComparisonChart
                spending={{
                  ...spending,
                  groceries,
                }}
                availableCards={availableCards}
                cardAId={cardAId}
                cardBId={cardBId}
                sliderMax={sliderMax}
              />
            </Suspense>
          )}

          {bestCard && secondCard && (
            <p className="simulator-summary">
              <strong>{bestCard.cardName}</strong> earns{" "}
              <strong>{formatMoney(difference)}</strong>{" "}
              more per year than {secondCard.cardName}.
            </p>
          )}
        </>
      )}
    </section>
  );
}
