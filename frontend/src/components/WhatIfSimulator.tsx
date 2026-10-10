
import { lazy, Suspense, useEffect, useState } from "react";
import "./WhatIfSimulator.css";
import type { Spending } from "../types/Spending";
import {
  isRankingProvisional,
  type Recommendation,
} from "../types/Recommendation";
import type { RewardSelectionRequest } from "../types/RewardSelection";
import {
  appendRewardSelection,
  requestKey,
} from "../utils/rewardSelection";

const CashbackComparisonChart = lazy(
  () => import("./CashbackComparisonChart")
);

type CreditCardOption = {
  id: number;
  cardName: string;
  rewardType: string;
};

type LoadedRecommendations = {
  key: string;
  // The inputs other than groceries; the chart does not depend on groceries.
  contextKey: string;
  data: Recommendation[];
};

type Failure = {
  key: string;
  message: string;
};

type Props = {
  spending: Spending;
  // Applied to every request so selections stay fixed as groceries change.
  selection: RewardSelectionRequest | null;
  // Range of the last consistent result; unaffected by pending updates.
  sliderMax: number;
  breakEvenMonthlyGroceries: number | null;
};

const formatMoney = (amount: number) =>
  new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
  }).format(amount);

export default function WhatIfSimulator({
  spending,
  selection,
  sliderMax,
  breakEvenMonthlyGroceries,
}: Props) {
  const [requestedGroceries, setGroceries] = useState(spending.groceries);
  // Clamped so a range change can never leave the value out of bounds.
  const groceries = Math.min(requestedGroceries, sliderMax);
  const [loaded, setLoaded] = useState<LoadedRecommendations | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const [availableCards, setAvailableCards] =
    useState<CreditCardOption[]>([]);
  const [cardsLoading, setCardsLoading] = useState(true);
  const [cardsError, setCardsError] = useState("");
  const [cardAId, setCardAId] = useState<number | null>(null);
  const [cardBId, setCardBId] = useState<number | null>(null);

  // Primitives keep effect dependencies stable across renders.
  const selectionCardId = selection?.cardId ?? null;
  const selectionCategories = selection?.value.categories.join(",") ?? "";
  const selectionConfirmed =
    selection?.value.extendedRequirementConfirmed ?? false;

  const contextKey = requestKey(
    spending.gas,
    spending.dining,
    spending.travel,
    spending.other,
    spending.transit,
    spending.rideshare,
    spending.evCharging,
    selectionCardId,
    selectionCategories,
    selectionConfirmed
  );
  const fullKey = requestKey(groceries, contextKey);

  // Results are shown as current only if computed for exactly these inputs.
  const recommendations = loaded?.data ?? [];
  const isCurrent = loaded?.key === fullKey;
  const contextCurrent = loaded?.contextKey === contextKey;
  const error = failure?.key === fullKey ? failure.message : "";
  const updating = !isCurrent && error === "";

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

      if (selectionCardId !== null) {
        appendRewardSelection(params, selectionCardId, {
          categories: selectionCategories
            ? selectionCategories.split(",")
            : [],
          extendedRequirementConfirmed: selectionConfirmed,
        });
      }

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
          setLoaded({ key: fullKey, contextKey, data });
          setFailure(null);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setFailure({
            key: fullKey,
            message:
              err instanceof Error
                ? err.message
                : "Simulation failed.",
          });
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
    selectionCardId,
    selectionCategories,
    selectionConfirmed,
    fullKey,
    contextKey,
    retryToken,
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
  const provisional = isCurrent && isRankingProvisional(recommendations);
  const showRanking = isCurrent && !provisional;
  const chartProvisional =
    contextCurrent && isRankingProvisional(recommendations);

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
        These are estimates; actual rewards depend on issuer rules and
        merchant classifications.
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

      {updating && recommendations.length === 0 && (
        <p className="simulator-loading" role="status">
          Updating recommendations...
        </p>
      )}

      {error && (
        <p className="error" role="alert">
          {error}
          {recommendations.length > 0 &&
            " Showing the last successful results, which are outdated."}{" "}
          <button
            type="button"
            className="simulator-retry"
            onClick={() => {
              setFailure(null);
              setRetryToken((token) => token + 1);
            }}
          >
            Retry
          </button>
        </p>
      )}

      {recommendations.length > 0 && (
        <>
          {!isCurrent && !error && (
            <p className="outdated-banner" role="status">
              Updating: the figures below are outdated until the new
              calculation finishes.
            </p>
          )}

          {provisional && (
            <p className="provisional-banner" role="status">
              Provisional comparison: a card is missing part of its category
              selection, so no card is marked as the best match.
            </p>
          )}

          <div
            className={`simulator-results${isCurrent ? "" : " is-outdated"}`}
            aria-busy={!isCurrent}
          >
            {recommendations.map((card, index) => (
              <div
                className="simulator-result"
                key={card.cardId}
              >
                <div className="simulator-card-header">
                  <span>{card.cardName}</span>

                  {index === 0 && showRanking && (
                    <span>Best Match</span>
                  )}

                  {card.selectionRequired && isCurrent && (
                    <span>Incomplete estimate</span>
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

          <div
            className={`simulator-chart${isCurrent ? "" : " is-outdated"}`}
          >
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
                selection={selection}
                provisional={chartProvisional}
                outdated={!contextCurrent}
              />
            </Suspense>
          )}

          {bestCard && secondCard && showRanking && (
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
