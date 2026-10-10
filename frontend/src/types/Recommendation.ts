export type Recommendation = {
  cardId: number;
  cardName: string;
  annualFee: number;
  annualReward: number;
  netAnnualReward: number;
  rewardBreakdown?: Record<string, number>;
  // Present only for cards with customizable reward categories.
  selectionMode?: "NONE_SELECTED" | "PARTIAL_SELECTION" | "SELECTED";
  selectionRequired?: boolean;
  selectedCategories?: string[];
};

// Any card still needing a selection makes the whole ranking provisional.
export const isRankingProvisional = (recommendations: Recommendation[]) =>
  recommendations.some((card) => card.selectionRequired === true);
