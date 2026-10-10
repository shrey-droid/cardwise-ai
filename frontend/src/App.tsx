
import { useEffect, useState } from "react";
import CardRewardSelection from "./components/CardRewardSelection";
import CardSource from "./components/CardSource";
import WhatIfSimulator from "./components/WhatIfSimulator";
import {
  BACKEND_SPENDING_CATEGORIES,
  type Spending,
} from "./types/Spending";
import {
  isRankingProvisional,
  type Recommendation,
} from "./types/Recommendation";
import {
  EMPTY_SELECTION,
  type CardCatalogueEntry,
  type RewardSelectionRequest,
  type SelectionsByCardId,
} from "./types/RewardSelection";
import {
  appendRewardSelection,
  reduceSelection,
  requestKey,
  selectionFromParts,
  selectionParts,
  type SelectionAction,
} from "./utils/rewardSelection";
import "./App.css";

type BreakEvenResult = {
  cardA: string;
  cardB: string;
  currentMonthlyGroceries: number;
  breakEvenMonthlyGroceries: number | null;
  additionalMonthlyGroceries: number | null;
  status: string;
  recommendation: string;
};

type Analysis = {
  key: string;
  recommendations: Recommendation[];
  breakEven: BreakEvenResult | null;
  // Fixed per result so an outdated result keeps its slider range.
  sliderMax: number;
};

type AnalysisFailure = {
  key: string;
  message: string;
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

function generateProvisionalExplanation(card: Recommendation): string {
  if (card.selectionRequired) {
    return (
      `This estimate is incomplete: ${card.cardName} does not yet have ` +
      `all of its required bonus categories selected, so only baseline ` +
      `rates are applied and its rewards are likely understated. Choose ` +
      `its categories above to complete the estimate.`
    );
  }

  return (
    `This estimate is complete, but it cannot be ranked definitively ` +
    `until every card with category choices has its selection completed.`
  );
}

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

  // Both results come from one request key, so they always agree.
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [analysisFailure, setAnalysisFailure] =
    useState<AnalysisFailure | null>(null);
  const [requested, setRequested] = useState(false);
  const [refreshToken, setRefreshToken] = useState(0);
  const [error, setError] = useState("");

  const [catalogueCards, setCatalogueCards] =
    useState<CardCatalogueEntry[]>([]);
  const [cardsError, setCardsError] = useState("");
  const [selections, setSelections] = useState<SelectionsByCardId>({});

  useEffect(() => {
    const controller = new AbortController();

    async function loadCards() {
      try {
        const response = await fetch(
          "/api/v1/cards?rewardType=CASHBACK",
          { signal: controller.signal }
        );

        if (!response.ok) {
          throw new Error("Unable to load card configuration.");
        }

        const cards: CardCatalogueEntry[] = await response.json();

        if (!controller.signal.aborted) {
          setCatalogueCards(cards);
          setCardsError("");
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setCardsError(
            err instanceof Error
              ? err.message
              : "Unable to load card configuration."
          );
        }
      }
    }

    // Deferred so React StrictMode's setup/cleanup pass can cancel first.
    const timeout = window.setTimeout(() => {
      void loadCards();
    }, 0);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, []);

  // The GET APIs carry one card's selection per request, so only the first
  // selectable card gets a selector; the rest are reported, not dropped.
  const selectableCards = catalogueCards.filter(
    (card) => card.selectionPolicy
  );
  const activeCard = selectableCards[0];
  const unsupportedCards = selectableCards.slice(1);
  const activeSelection = activeCard
    ? (selections[activeCard.id] ?? EMPTY_SELECTION)
    : null;

  const selectionRequest: RewardSelectionRequest | null =
    activeCard && activeSelection
      ? { cardId: activeCard.id, value: activeSelection }
      : null;

  // Primitive snapshot of the selection; effects and keys depend on this.
  const {
    cardId: selectionCardId,
    categories: selectionCategories,
    confirmed: selectionConfirmed,
  } = selectionParts(selectionRequest);

  const analysisKey = requestKey(
    JSON.stringify(spending),
    selectionCardId,
    selectionCategories,
    selectionConfirmed
  );

  const recommendations = analysis?.recommendations ?? [];
  const breakEven = analysis?.breakEven ?? null;

  // Results are current only if computed for exactly the inputs on screen.
  const resultsCurrent = analysis !== null && analysis.key === analysisKey;
  const failureMessage =
    analysisFailure?.key === analysisKey ? analysisFailure.message : "";
  const updating = requested && !resultsCurrent && failureMessage === "";
  const provisional = resultsCurrent && isRankingProvisional(recommendations);
  const showRanking = resultsCurrent && !provisional;
  const hasDemoCard = recommendations.some(
    (card) => catalogueCards.find((c) => c.id === card.cardId)?.demo
  );

  useEffect(() => {
    if (!requested) return;

    const controller = new AbortController();
    const { signal } = controller;
    const key = requestKey(
      JSON.stringify(spending),
      selectionCardId,
      selectionCategories,
      selectionConfirmed
    );
    const request = selectionFromParts({
      cardId: selectionCardId,
      categories: selectionCategories,
      confirmed: selectionConfirmed,
    });

    // Debounced so rapid changes collapse; stale requests are aborted below.
    const timeout = setTimeout(async () => {
      try {
        const params = new URLSearchParams(
          Object.entries(spending).map(([name, value]) => [
            name,
            String(value),
          ])
        );
        if (request) {
          appendRewardSelection(params, request.cardId, request.value);
        }

        const response = await fetch(
          `/api/v1/recommendations?${params.toString()}`,
          { signal }
        );

        if (!response.ok) {
          throw new Error("Unable to fetch recommendations.");
        }

        const data: Recommendation[] = await response.json();

        const groceryCard = data.find(
          (card) => card.cardName === "Grocery Rewards Plus"
        );
        const everydayCard = data.find(
          (card) => card.cardName === "Everyday Cashback"
        );

        // Demo pair when present; otherwise compare the top two cards.
        const pair =
          groceryCard && everydayCard
            ? [groceryCard, everydayCard]
            : data.length >= 2
              ? [data[0], data[1]]
              : null;

        let breakEvenResult: BreakEvenResult | null = null;

        if (pair) {
          const breakEvenParams = new URLSearchParams({
            cardAId: String(pair[0].cardId),
            cardBId: String(pair[1].cardId),
            groceries: String(spending.groceries),
            gas: String(spending.gas),
            dining: String(spending.dining),
            travel: String(spending.travel),
            other: String(spending.other),
            transit: String(spending.transit),
            rideshare: String(spending.rideshare),
            evCharging: String(spending.evCharging),
          });
          if (request) {
            appendRewardSelection(
              breakEvenParams,
              request.cardId,
              request.value
            );
          }

          try {
            const breakEvenResponse = await fetch(
              `/api/v1/break-even?${breakEvenParams.toString()}`,
              { signal }
            );

            if (!breakEvenResponse.ok) {
              throw new Error("Break-even API request failed");
            }

            breakEvenResult = await breakEvenResponse.json();
          } catch (breakEvenError) {
            if (signal.aborted) return;
            console.error(
              "Unable to load break-even analysis:",
              breakEvenError
            );
          }
        }

        if (signal.aborted) return;
        setAnalysis({
          key,
          recommendations: data,
          breakEven: breakEvenResult,
          sliderMax: Math.max(
            1000,
            spending.groceries,
            breakEvenResult?.status === "BREAK_EVEN_FOUND"
              ? (breakEvenResult.breakEvenMonthlyGroceries ?? 0)
              : 0
          ),
        });
        setAnalysisFailure(null);
      } catch (err) {
        if (signal.aborted) return;
        setAnalysisFailure({
          key,
          message:
            err instanceof Error ? err.message : "Something went wrong.",
        });
      }
    }, 120);

    return () => {
      clearTimeout(timeout);
      controller.abort();
    };
  }, [
    requested,
    refreshToken,
    spending,
    selectionCardId,
    selectionCategories,
    selectionConfirmed,
  ]);

  const updateSpending = (
    category: keyof Spending,
    value: number
  ) => {
    setSpending((previous) => ({
      ...previous,
      [category]: value,
    }));

    // Hide old results when spending changes; the effect aborts pending calls.
    setRequested(false);
    setAnalysis(null);
    setAnalysisFailure(null);
  };

  const findBestCard = () => {
    if (
      Object.values(spending).some(
        (amount) => !Number.isFinite(amount) || amount < 0
      )
    ) {
      setError("Please enter valid non-negative amounts.");
      return;
    }

    setError("");
    setAnalysisFailure(null);
    setRequested(true);
    setRefreshToken((token) => token + 1);
  };

  const handleSelectionAction = (action: SelectionAction) => {
    const policy = activeCard?.selectionPolicy;
    if (!activeCard || !policy) return;

    const cardId = activeCard.id;
    const rules = {
      baseSelectionLimit: policy.baseSelectionLimit,
      extendedSelectionLimit: policy.extendedSelectionLimit,
      offeredCodes: new Set(policy.selectableCategories.map((c) => c.code)),
    };

    // Functional update: always applied to the latest state, never a snapshot.
    setSelections((previous) => ({
      ...previous,
      [cardId]: reduceSelection(
        previous[cardId] ?? EMPTY_SELECTION,
        action,
        rules
      ),
    }));
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

      {cardsError && <p className="error">{cardsError}</p>}

      {activeCard?.selectionPolicy && activeSelection && (
        <section className="selection-section">
          <CardRewardSelection
            cardName={activeCard.cardName}
            categories={activeCard.selectionPolicy.selectableCategories}
            baseSelectionLimit={activeCard.selectionPolicy.baseSelectionLimit}
            extendedSelectionLimit={
              activeCard.selectionPolicy.extendedSelectionLimit
            }
            extendedRequirementLabel={
              activeCard.selectionPolicy.extendedRequirementLabel
            }
            modelledCategoryCount={
              activeCard.selectionPolicy.modelledCategoryCount
            }
            offeredCategoryCount={
              activeCard.selectionPolicy.offeredCategoryCount
            }
            value={activeSelection}
            onToggleCategory={(code) =>
              handleSelectionAction({ type: "toggleCategory", code })
            }
            onExtendedChange={(confirmed) =>
              handleSelectionAction({
                type: "setExtendedConfirmed",
                confirmed,
              })
            }
          />

          {activeCard.selectionPolicy.changeHoldDays !== null && (
            <p className="selection-footnote">
              Your selections describe what you want to compare. CardWise does
              not verify your account settings, and the issuer may apply
              category changes only after a{" "}
              {activeCard.selectionPolicy.changeHoldDays}-day hold.
            </p>
          )}
        </section>
      )}

      {unsupportedCards.length > 0 && (
        <p className="selection-warning" role="alert">
          Category selections for{" "}
          {unsupportedCards.map((card) => card.cardName).join(", ")} cannot
          be applied yet, because each request carries one card&apos;s
          selections. Those cards are shown as incomplete estimates.
        </p>
      )}

      <button
        type="button"
        onClick={findBestCard}
        disabled={updating}
      >
        {updating
          ? "Finding Best Card..."
          : "Find My Best Credit Card"}
      </button>

      {error && <p className="error">{error}</p>}

      {analysis && !resultsCurrent && (
        <p
          className="outdated-banner"
          role={failureMessage ? "alert" : "status"}
        >
          {failureMessage
            ? `Unable to update results: ${failureMessage} The figures below are from your previous inputs and are outdated.`
            : "Updating results for your new selection. The figures below are outdated until the update finishes."}
        </p>
      )}

      {!analysis && failureMessage && (
        <p className="error" role="alert">
          {failureMessage}
        </p>
      )}

      {breakEven && (
        <section
          className={`break-even-card${resultsCurrent ? "" : " is-outdated"}`}
          aria-busy={!resultsCurrent}
        >
          <h2>Grocery Spending Break-Even Analysis</h2>

          {breakEven.status === "BREAK_EVEN_FOUND" &&
          breakEven.breakEvenMonthlyGroceries !== null ? (
            <>
              {breakEven.cardA === "Grocery Rewards Plus" &&
              breakEven.cardB === "Everyday Cashback" ? (
                <p>
                  Grocery Rewards Plus becomes more profitable
                  than Everyday Cashback above:
                </p>
              ) : (
                <p>{breakEven.recommendation}</p>
              )}

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
          {provisional && (
            <p className="break-even-note provisional-note">
              Provisional: this comparison uses an incomplete category
              selection.
            </p>
          )}
        </section>
      )}

      {recommendations.length > 0 && (
        <section
          className={`results${provisional ? " is-provisional" : ""}${
            resultsCurrent ? "" : " is-outdated"
          }`}
          aria-busy={!resultsCurrent}
        >
          <h2>Your Recommended Credit Cards</h2>

          {provisional && (
            <p className="provisional-banner" role="status">
              Provisional ranking: at least one card still needs its category
              selection, so no card is marked as the best match yet. Cards are
              listed in provisional order of estimated rewards.
            </p>
          )}

          {recommendations.map((card, index) => (
            <article
              className="recommendation-card"
              key={card.cardId}
            >
              <div className="card-heading">
                <h3>
                  {showRanking
                    ? `${index + 1}. ${card.cardName}`
                    : card.cardName}
                </h3>

                {index === 0 && showRanking && (
                  <span className="best-badge">
                    Best Match
                  </span>
                )}

                {card.selectionRequired && resultsCurrent && (
                  <span className="incomplete-badge">
                    Incomplete estimate
                  </span>
                )}
              </div>

              <CardSource entry={catalogueCards.find((c) => c.id === card.cardId)} />

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
                  {!resultsCurrent
                    ? "These figures are outdated and are being recalculated."
                    : provisional
                      ? generateProvisionalExplanation(card)
                      : generateExplanation(
                          card,
                          recommendations[0],
                          recommendations
                        )}
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
            CardWise AI provides estimated rewards based on publicly available
            card terms and the spending information you enter. Actual rewards
            depend on issuer eligibility rules, merchant classifications,
            exclusions, and current terms. {hasDemoCard &&
              "Demo cards are illustrative and are not real financial products. "}
            Always verify details with the card issuer before applying.
            CardWise AI is an independent tool and is not affiliated with,
            endorsed by, or sponsored by any card issuer.
          </p>
        </section>
      )}

      {recommendations.length > 0 && (
        <WhatIfSimulator
          spending={spending}
          selection={selectionRequest}
          sliderMax={analysis?.sliderMax ?? 1000}
          breakEvenMonthlyGroceries={
            resultsCurrent && breakEven?.status === "BREAK_EVEN_FOUND"
              ? breakEven.breakEvenMonthlyGroceries
              : null
          }
        />
      )}
    </main>
  );
}
