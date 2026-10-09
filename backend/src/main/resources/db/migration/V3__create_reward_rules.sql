CREATE TABLE reward_rules (
    id BIGSERIAL PRIMARY KEY,
    credit_card_id BIGINT NOT NULL REFERENCES credit_cards(id),
    spending_category VARCHAR(50) NOT NULL,
    reward_rate NUMERIC(7, 4) NOT NULL,
    UNIQUE (credit_card_id, spending_category),
    CHECK (reward_rate >= 0)
);

INSERT INTO reward_rules (credit_card_id, spending_category, reward_rate)
SELECT id, 'GROCERIES', 1.0000
FROM credit_cards
WHERE card_name = 'Everyday Cashback';

INSERT INTO reward_rules (credit_card_id, spending_category, reward_rate)
SELECT id, 'GROCERIES', 4.0000
FROM credit_cards
WHERE card_name = 'Grocery Rewards Plus';