-- Support cards whose bonus rate applies only to cardholder-selected categories.
-- Existing rules default to non-selectable, so current cards are unaffected.

ALTER TABLE reward_rules
    ADD COLUMN is_selectable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN unselected_reward_rate NUMERIC(7, 4);

-- For selectable rules, reward_rate is the selected rate.
ALTER TABLE reward_rules
    ADD CONSTRAINT chk_selectable_rule_rates
    CHECK (
        (
            is_selectable
            AND unselected_reward_rate IS NOT NULL
            AND unselected_reward_rate >= 0
            AND unselected_reward_rate <= reward_rate
        )
        OR (
            NOT is_selectable
            AND unselected_reward_rate IS NULL
        )
    );

CREATE TABLE card_selection_policies (
    credit_card_id BIGINT PRIMARY KEY
        REFERENCES credit_cards (id) ON DELETE CASCADE,
    base_selection_limit INTEGER NOT NULL,
    extended_selection_limit INTEGER NOT NULL,
    extended_requirement VARCHAR(100),
    change_hold_days INTEGER,
    CONSTRAINT chk_base_selection_limit
        CHECK (base_selection_limit > 0),
    CONSTRAINT chk_extended_selection_limit
        CHECK (extended_selection_limit >= base_selection_limit),
    CONSTRAINT chk_change_hold_days
        CHECK (change_hold_days IS NULL OR change_hold_days >= 0)
);
