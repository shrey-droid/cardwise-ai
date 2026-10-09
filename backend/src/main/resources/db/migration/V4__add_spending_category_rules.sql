INSERT INTO reward_rules
    (credit_card_id, spending_category, reward_rate)
SELECT c.id, r.category, r.rate
FROM credit_cards c
CROSS JOIN (
    VALUES
        ('GAS', 1.0000),
        ('DINING', 1.0000),
        ('TRAVEL', 1.0000),
        ('OTHER', 1.0000)
) AS r(category, rate)
WHERE c.card_name = 'Everyday Cashback'
  AND c.issuer = 'Demo Bank A';

INSERT INTO reward_rules
    (credit_card_id, spending_category, reward_rate)
SELECT c.id, r.category, r.rate
FROM credit_cards c
CROSS JOIN (
    VALUES
        ('GAS', 2.0000),
        ('DINING', 2.0000),
        ('TRAVEL', 1.0000),
        ('OTHER', 1.0000)
) AS r(category, rate)
WHERE c.card_name = 'Grocery Rewards Plus'
  AND c.issuer = 'Demo Bank B';