import { useId } from "react";
import "./CardRewardSelection.css";
import {
  getSelectionStatus,
  type RewardSelectionValue,
  type SelectableCategory,
  type SelectionStatus,
} from "../types/RewardSelection";

type Props = {
  cardName: string;
  categories: SelectableCategory[];
  baseSelectionLimit: number;
  /** Larger than baseSelectionLimit enables the confirmation checkbox. */
  extendedSelectionLimit: number;
  /** User-facing wording of the requirement for the extra categories. */
  extendedRequirementLabel: string;
  /** How many of the issuer's selectable categories CardWise models. */
  modelledCategoryCount: number;
  /** How many selectable categories the issuer offers in total. */
  offeredCategoryCount: number;
  value: RewardSelectionValue;
  // Intents, not values: the parent applies them to its latest state.
  onToggleCategory: (code: string) => void;
  onExtendedChange: (confirmed: boolean) => void;
  disabled?: boolean;
};

const formatRate = (rate: number) => `${Number(rate.toFixed(2))}%`;

function statusText(
  status: SelectionStatus,
  selectedCount: number,
  baseSelectionLimit: number
) {
  if (status === "SELECTED") return "Complete";
  if (status === "PARTIAL_SELECTION") {
    return `Incomplete: ${selectedCount} of ${baseSelectionLimit} required categories selected`;
  }
  return `Not started: choose ${baseSelectionLimit} categories`;
}

export default function CardRewardSelection({
  cardName,
  categories,
  baseSelectionLimit,
  extendedSelectionLimit,
  extendedRequirementLabel,
  modelledCategoryCount,
  offeredCategoryCount,
  value,
  onToggleCategory,
  onExtendedChange,
  disabled = false,
}: Props) {
  const idPrefix = useId();
  const offeredCodes = new Set(categories.map((category) => category.code));
  const selected = value.categories.filter((code) => offeredCodes.has(code));

  const canExtend = extendedSelectionLimit > baseSelectionLimit;
  const limit =
    canExtend && value.extendedRequirementConfirmed
      ? extendedSelectionLimit
      : baseSelectionLimit;
  const atLimit = selected.length >= limit;

  const status = getSelectionStatus(selected.length, baseSelectionLimit);
  const transitCategory = categories.find(
    (category) => category.code === "TRANSIT"
  );

  return (
    <section className="reward-selection" aria-label={`${cardName} cashback preferences`}>
      <h3 className="reward-selection-title">{cardName} cashback preferences</h3>

      <fieldset className="reward-selection-fieldset" disabled={disabled}>
        <legend className="reward-selection-legend">
          Your bonus categories
          <span className="reward-selection-count">
            {selected.length} of {limit} selected
          </span>
        </legend>

        <ul className="reward-selection-list">
          {categories.map((category) => {
            const isSelected = selected.includes(category.code);
            const isBlocked = !isSelected && atLimit;
            const inputId = `${idPrefix}-${category.code}`;

            return (
              <li key={category.code}>
                <label
                  htmlFor={inputId}
                  className={`reward-selection-option${
                    isSelected ? " is-selected" : ""
                  }${isBlocked ? " is-blocked" : ""}`}
                >
                  <input
                    id={inputId}
                    type="checkbox"
                    checked={isSelected}
                    disabled={isBlocked}
                    onChange={() => onToggleCategory(category.code)}
                  />
                  <span className="reward-selection-label">{category.label}</span>
                  <span className="reward-selection-rate">
                    {isSelected
                      ? `${formatRate(category.selectedRate)} cashback`
                      : `${formatRate(category.unselectedRate)} baseline`}
                  </span>
                </label>
              </li>
            );
          })}
        </ul>

        {atLimit && (
          <p className="reward-selection-hint">
            Selection limit reached. Unselect a category to choose a different one.
          </p>
        )}
      </fieldset>

      {canExtend && (
        <label className="reward-selection-extended" htmlFor={`${idPrefix}-extended`}>
          <input
            id={`${idPrefix}-extended`}
            type="checkbox"
            checked={value.extendedRequirementConfirmed}
            disabled={disabled}
            onChange={(event) => onExtendedChange(event.target.checked)}
          />
          <span>
            {extendedRequirementLabel}
            <small>
              Optional. This unlocks up to {extendedSelectionLimit} categories;
              {" "}{baseSelectionLimit} are enough for a complete estimate.
            </small>
          </span>
        </label>
      )}

      <div
        className={`reward-selection-status status-${status.toLowerCase()}`}
        role="status"
      >
        <span className="reward-selection-status-label">Selection status</span>
        <strong>{statusText(status, selected.length, baseSelectionLimit)}</strong>
      </div>

      <p className="reward-selection-note">
        Selected categories earn the higher estimated rate; other spending earns
        the baseline rate, subject to the issuer&apos;s merchant classifications.
      </p>
      <p className="reward-selection-note reward-selection-limitation">
        CardWise models only {modelledCategoryCount} of {cardName}&apos;s{" "}
        {offeredCategoryCount} selectable categories. Spending in the others is
        counted as &quot;Other&quot; at the baseline rate, so this is not a
        complete estimate for every spending profile.
      </p>
      {transitCategory && (
        <p className="reward-selection-note reward-selection-limitation">
          Our Transit spending input is broader than the issuer&apos;s &quot;
          {transitCategory.label}&quot; category, and parking is not captured as
          a separate spending category. Qualifying spend may be lower than the
          amount you enter.
        </p>
      )}
    </section>
  );
}
