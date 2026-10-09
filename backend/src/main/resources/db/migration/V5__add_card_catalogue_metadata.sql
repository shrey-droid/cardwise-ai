-- CardWise AI: Canadian credit card catalogue metadata
-- issuer already exists as VARCHAR(100) NOT NULL from V1.

ALTER TABLE credit_cards
    ADD COLUMN minimum_personal_income NUMERIC(12, 2),
    ADD COLUMN minimum_household_income NUMERIC(12, 2),
    ADD COLUMN official_url TEXT,
    ADD COLUMN last_verified_at DATE;

-- Additional metadata for category-specific reward rules
ALTER TABLE reward_rules
    ADD COLUMN spending_cap NUMERIC(12, 2),
    ADD COLUMN cap_period VARCHAR(20),
    ADD COLUMN conditions TEXT;

-- Prevent negative income requirements
ALTER TABLE credit_cards
    ADD CONSTRAINT chk_min_personal_income
    CHECK (
        minimum_personal_income IS NULL
        OR minimum_personal_income >= 0
    );

ALTER TABLE credit_cards
    ADD CONSTRAINT chk_min_household_income
    CHECK (
        minimum_household_income IS NULL
        OR minimum_household_income >= 0
    );

-- Prevent invalid spending caps
ALTER TABLE reward_rules
    ADD CONSTRAINT chk_spending_cap
    CHECK (
        spending_cap IS NULL
        OR spending_cap >= 0
    );

-- Restrict cap periods
ALTER TABLE reward_rules
    ADD CONSTRAINT chk_cap_period
    CHECK (
        cap_period IS NULL
        OR cap_period IN ('MONTHLY', 'ANNUAL')
    );

-- A cap period only makes sense when a cap exists
ALTER TABLE reward_rules
    ADD CONSTRAINT chk_cap_period_requires_cap
    CHECK (
        cap_period IS NULL
        OR spending_cap IS NOT NULL
    );
