ALTER TABLE reward_rules
    ADD COLUMN base_reward_rate NUMERIC(7, 4);

ALTER TABLE reward_rules
    ADD CONSTRAINT chk_base_reward_rate
    CHECK (
        base_reward_rate IS NULL
        OR base_reward_rate BETWEEN 0 AND 100
    );

ALTER TABLE reward_rules
    ADD CONSTRAINT chk_capped_rule_requires_fallback
    CHECK (
        spending_cap IS NULL
        OR (
            cap_period IS NOT NULL
            AND base_reward_rate IS NOT NULL
        )
    );
