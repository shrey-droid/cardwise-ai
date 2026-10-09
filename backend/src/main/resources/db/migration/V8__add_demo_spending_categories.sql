-- Add three spending categories to existing demo cards.
-- These are fictional development rules, not real issuer terms.
--
-- Each new category inherits its demo card's OTHER reward rule.
-- Existing reward rules remain unchanged.

INSERT INTO reward_rules (
    credit_card_id,
    spending_category,
    reward_rate,
    spending_cap,
    cap_period,
    base_reward_rate,
    conditions
)
SELECT
    c.id,
    new_category.spending_category,
    source.reward_rate,
    source.spending_cap,
    source.cap_period,
    source.base_reward_rate,
    source.conditions
FROM credit_cards c
JOIN reward_rules source
    ON source.credit_card_id = c.id
   AND source.spending_category = 'OTHER'
CROSS JOIN (
    VALUES
        ('TRANSIT'),
        ('RIDESHARE'),
        ('EV_CHARGING')
) AS new_category(spending_category)
WHERE c.is_demo = TRUE
ON CONFLICT (credit_card_id, spending_category)
DO NOTHING;
