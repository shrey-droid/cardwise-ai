import type {
  RewardSelectionRequest,
  RewardSelectionValue,
} from "../types/RewardSelection";

export function appendRewardSelection(
  params: URLSearchParams,
  cardId: number,
  selection: RewardSelectionValue
) {
  params.set("cardId", String(cardId));

  if (selection.categories.length > 0) {
    params.set("selectedCategories", selection.categories.join(","));
  }

  params.set(
    "extendedRequirementConfirmed",
    String(selection.extendedRequirementConfirmed)
  );
}

export type SelectionAction =
  | { type: "toggleCategory"; code: string }
  | { type: "setExtendedConfirmed"; confirmed: boolean };

export type SelectionRules = {
  baseSelectionLimit: number;
  extendedSelectionLimit: number;
  offeredCodes: ReadonlySet<string>;
};

// Applied to the latest state via a functional update, so rapid events
// compose and the limits hold no matter how many are queued.
export function reduceSelection(
  current: RewardSelectionValue,
  action: SelectionAction,
  rules: SelectionRules
): RewardSelectionValue {
  const canExtend = rules.extendedSelectionLimit > rules.baseSelectionLimit;

  if (action.type === "setExtendedConfirmed") {
    const confirmed = canExtend && action.confirmed;
    const limit = confirmed
      ? rules.extendedSelectionLimit
      : rules.baseSelectionLimit;

    return {
      extendedRequirementConfirmed: confirmed,
      categories: current.categories.slice(0, limit),
    };
  }

  if (!rules.offeredCodes.has(action.code)) {
    return current;
  }

  if (current.categories.includes(action.code)) {
    return {
      ...current,
      categories: current.categories.filter((code) => code !== action.code),
    };
  }

  const limit =
    canExtend && current.extendedRequirementConfirmed
      ? rules.extendedSelectionLimit
      : rules.baseSelectionLimit;

  if (current.categories.length >= limit) {
    return current;
  }

  return { ...current, categories: [...current.categories, action.code] };
}

// Primitive form of a selection, safe to use as effect dependencies.
export type SelectionParts = {
  cardId: number | null;
  categories: string;
  confirmed: boolean;
};

export function selectionParts(
  selection: RewardSelectionRequest | null
): SelectionParts {
  return {
    cardId: selection?.cardId ?? null,
    categories: selection?.value.categories.join(",") ?? "",
    confirmed: selection?.value.extendedRequirementConfirmed ?? false,
  };
}

export function selectionFromParts(
  parts: SelectionParts
): RewardSelectionRequest | null {
  return parts.cardId === null
    ? null
    : {
        cardId: parts.cardId,
        value: {
          categories: parts.categories ? parts.categories.split(",") : [],
          extendedRequirementConfirmed: parts.confirmed,
        },
      };
}

// Identifies the exact inputs a result was computed for.
export const requestKey = (
  ...parts: (string | number | boolean | null)[]
) => parts.map(String).join("|");
