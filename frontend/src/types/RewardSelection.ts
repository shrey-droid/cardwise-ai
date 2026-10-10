export type SelectionStatus =
  | "NONE_SELECTED"
  | "PARTIAL_SELECTION"
  | "SELECTED";

export type SelectableCategory = {
  /** Backend spending category code, e.g. "GROCERIES". */
  code: string;
  label: string;
  /** Percent earned when the category is selected. */
  selectedRate: number;
  /** Percent earned when it is not selected. */
  unselectedRate: number;
};

export type RewardSelectionValue = {
  categories: string[];
  extendedRequirementConfirmed: boolean;
};

export const EMPTY_SELECTION: RewardSelectionValue = {
  categories: [],
  extendedRequirementConfirmed: false,
};

/** Mirrors the selectionPolicy object returned by GET /api/v1/cards. */
export type SelectionPolicy = {
  baseSelectionLimit: number;
  extendedSelectionLimit: number;
  extendedRequirement: string;
  extendedRequirementLabel: string;
  changeHoldDays: number | null;
  modelledCategoryCount: number;
  offeredCategoryCount: number;
  selectableCategories: SelectableCategory[];
};

export type CardCatalogueEntry = {
  id: number;
  cardName: string;
  rewardType: string;
  demo: boolean;
  officialUrl: string | null;
  lastVerifiedAt: string | null;
  selectionPolicy?: SelectionPolicy;
};

export type SelectionsByCardId = Record<number, RewardSelectionValue>;

/** The one card selection a single API request can carry. */
export type RewardSelectionRequest = {
  cardId: number;
  value: RewardSelectionValue;
};

// Mirrors the backend: complete once the base limit is reached.
export function getSelectionStatus(
  selectedCount: number,
  baseSelectionLimit: number
): SelectionStatus {
  if (selectedCount === 0) return "NONE_SELECTED";
  return selectedCount >= baseSelectionLimit
    ? "SELECTED"
    : "PARTIAL_SELECTION";
}
