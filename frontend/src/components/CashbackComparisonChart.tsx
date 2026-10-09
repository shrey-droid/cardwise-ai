import { useEffect, useState } from "react";

import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

type Spending = {
  groceries: number;
  gas: number;
  dining: number;
  travel: number;
  other: number;
};

type CreditCardOption = {
  id: number;
  cardName: string;
  rewardType: string;
};

type SimulationPoint = {
  groceries: number;
  rewards: Record<string, number>;
};

type TieInterval = {
  start: number;
  end: number | null;
};

type SimulationResponse = {
  category: string;
  maxGroceries: number;
  cardAId: number;
  cardBId: number;
  breakEvenMonthlyGroceries: number | null;
  breakEvenPoints?: number[];
  tieIntervals?: TieInterval[];
  status: string;
  recommendation: string;
  points: SimulationPoint[];
};

type Props = {
  spending: Spending;
  availableCards: CreditCardOption[];
  cardAId: number;
  cardBId: number;
  sliderMax: number;
};

type LoadedSimulation = {
  key: string;
  data: SimulationResponse;
};

type SimulationError = {
  key: string;
  message: string;
};

const money = (value: number) =>
  new Intl.NumberFormat("en-CA", {
    style: "currency",
    currency: "CAD",
  }).format(value);

export default function CashbackComparisonChart({
  spending,
  availableCards,
  cardAId,
  cardBId,
  sliderMax,
}: Props) {
  const [loadedSimulation, setLoadedSimulation] =
    useState<LoadedSimulation | null>(null);
  const [simulationError, setSimulationError] =
    useState<SimulationError | null>(null);
  const [loadingKey, setLoadingKey] = useState<string | null>(null);

  const requestKey = [
    cardAId,
    cardBId,
    spending.gas,
    spending.dining,
    spending.travel,
    spending.other,
    sliderMax,
  ].join("|");

  const simulation =
    loadedSimulation?.key === requestKey
      ? loadedSimulation.data
      : null;
  const error =
    simulationError?.key === requestKey
      ? simulationError.message
      : "";
  const loading = loadingKey === requestKey ||
    (simulation === null && error === "");

  useEffect(() => {
    const controller = new AbortController();

    const params = new URLSearchParams({
      cardAId: String(cardAId),
      cardBId: String(cardBId),
      gas: String(spending.gas),
      dining: String(spending.dining),
      travel: String(spending.travel),
      other: String(spending.other),
      maxGroceries: String(sliderMax),
    });

    const timeout = setTimeout(async () => {
      setLoadingKey(requestKey);

      try {
        const response = await fetch(
          `/api/v1/simulations/groceries?${params.toString()}`,
          { signal: controller.signal }
        );

        if (!response.ok) {
          throw new Error(
            "Unable to load cashback simulation."
          );
        }

        const data: SimulationResponse =
          await response.json();

        if (!controller.signal.aborted) {
          setLoadedSimulation({ key: requestKey, data });
          setSimulationError(null);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setSimulationError({
            key: requestKey,
            message:
              err instanceof Error
                ? err.message
                : "Simulation failed.",
          });
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoadingKey(null);
        }
      }
    }, 300);

    return () => {
      clearTimeout(timeout);
      controller.abort();
    };
  }, [
    cardAId,
    cardBId,
    spending.gas,
    spending.dining,
    spending.travel,
    spending.other,
    sliderMax,
    requestKey,
  ]);

  const first = availableCards.find(
    (card) => card.id === cardAId
  );
  const second = availableCards.find(
    (card) => card.id === cardBId
  );

  if (!first || !second || cardAId === cardBId) {
    return null;
  }

  const data =
    simulation?.points.map((point) => ({
      groceries: point.groceries,
      firstReward:
        point.rewards[String(first.id)] ?? null,
      secondReward:
        point.rewards[String(second.id)] ?? null,
    })) ?? [];

  const chartBreakEvenPoints = simulation?.breakEvenPoints?.length
    ? [...new Set(simulation.breakEvenPoints)].sort((a, b) => a - b)
    : simulation?.breakEvenMonthlyGroceries !== null &&
        simulation?.breakEvenMonthlyGroceries !== undefined &&
        (simulation.status === "BREAK_EVEN_FOUND" ||
          simulation.status === "MULTIPLE_CROSSOVERS")
      ? [simulation.breakEvenMonthlyGroceries]
      : [];

  const visibleBreakEvenPoints = chartBreakEvenPoints.filter(
    (point) => point >= 0 && point <= sliderMax
  );
  const outsideRangeBreakEvenPoints = chartBreakEvenPoints.filter(
    (point) => point < 0 || point > sliderMax
  );
  const tieIntervals = simulation?.tieIntervals ?? [];

  const visibleTieIntervals = tieIntervals
    .map((interval) => ({
      start: Math.max(0, interval.start),
      end: Math.min(sliderMax, interval.end ?? sliderMax),
    }))
    .filter((interval) => interval.start < interval.end);

  return (
    <div className="cashback-comparison-chart">
      <h3>Net Annual Cashback Comparison</h3>

      <p>
        Compare credit card rewards across different
        monthly grocery spending amounts.
      </p>

      {loading && <p>Loading simulation chart...</p>}

      {error && <p className="error">{error}</p>}

      {!loading && !error && data.length > 0 && (
        <div className="cashback-chart-container">
          <ResponsiveContainer width="100%" height={320}>
            <LineChart
              data={data}
              margin={{
                top: 15,
                right: 20,
                bottom: 20,
                left: 10,
              }}
            >
              <CartesianGrid strokeDasharray="3 3" />

              {visibleTieIntervals.map((interval, index) => (
                <ReferenceArea
                  key={`tie-interval-${index}`}
                  x1={interval.start}
                  x2={interval.end}
                  fill="#a78bfa"
                  fillOpacity={0.16}
                  strokeOpacity={0}
                  ifOverflow="hidden"
                />
              ))}

              <XAxis
                dataKey="groceries"
                type="number"
                domain={[0, sliderMax]}
                tickFormatter={(value: number) => `$${value}`}
                label={{
                  value: "Monthly Grocery Spending",
                  position: "insideBottom",
                  offset: -10,
                }}
              />

              <YAxis
                tickFormatter={(value: number) => `$${value}`}
              />

              <Tooltip
                formatter={(value, name) => [
                  money(Number(value)),
                  String(name),
                ]}
                labelFormatter={(value) =>
                  `Monthly groceries: ${money(Number(value))}`
                }
              />

              <Legend verticalAlign="top" height={40} />

              <Line
                type="linear"
                dataKey="firstReward"
                name={first.cardName}
                stroke="#2563eb"
                strokeWidth={3}
                dot={false}
                activeDot={{ r: 6 }}
                isAnimationActive={false}
              />

              <Line
                type="linear"
                dataKey="secondReward"
                name={second.cardName}
                stroke="#16a34a"
                strokeWidth={3}
                dot={false}
                activeDot={{ r: 6 }}
                isAnimationActive={false}
              />

              {visibleBreakEvenPoints.map((chartBreakEven, index) => (
                  <ReferenceLine
                    key={`break-even-${chartBreakEven}`}
                    x={chartBreakEven}
                    stroke="#dc2626"
                    strokeDasharray="5 5"
                    label={{
                      value: `Break-even ${money(chartBreakEven)}`,
                      position: index % 2 === 0
                        ? "insideTopRight"
                        : "insideTopLeft",
                      fill: "#dc2626",
                      fontSize: 11,
                    }}
                  />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {!loading && !error && simulation && (
        <>
          <p className="simulator-chart-note">
            {simulation.recommendation}
          </p>

          {chartBreakEvenPoints.length > 0 && (
            <p className="simulator-chart-note">
              Break-even points: {chartBreakEvenPoints
                .map((point) => money(point))
                .join(", ")} / month
              {outsideRangeBreakEvenPoints.length > 0 && (
                <>; {outsideRangeBreakEvenPoints
                  .map((point) => money(point))
                  .join(", ")} outside the visible chart range</>
              )}
            </p>
          )}

          {tieIntervals.length > 0 && (
            <div className="simulator-chart-note">
              <strong>Equal cashback ranges:</strong>
              <ul>
                {tieIntervals.map((interval, index) => (
                  <li key={`${interval.start}-${index}`}>
                    {money(interval.start)} to{" "}
                    {interval.end === null
                      ? "any higher monthly grocery spending"
                      : money(interval.end)}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </div>
  );
}
