-- RBC Cash Back Mastercard
-- Standard cashback rates effective October 1, 2026.
-- Source: https://www.rbcroyalbank.com/credit-cards/cardholders/cash-back-updates.html
-- MCC eligibility applies. Promotional bonuses excluded.

INSERT INTO credit_cards (
	card_name,
	issuer,
	reward_type,
	annual_fee,
	is_demo,
	official_url,
	last_verified_at
)
VALUES (
	'RBC Cash Back Mastercard',
	'RBC',
	'CASHBACK',
	0.00,
	FALSE,
	'https://www.rbcroyalbank.com/credit-cards/cardholders/cash-back-updates.html',
	DATE '2026-10-09'
);

INSERT INTO reward_rules (
	credit_card_id,
	spending_category,
	reward_rate,
	conditions
)
SELECT
	c.id,
	r.category,
	r.rate,
	r.conditions
FROM credit_cards c
CROSS JOIN (
	VALUES
		('GROCERIES', 2.0000, 'Eligible Mastercard grocery merchant classifications only.'),
		('GAS', 1.0000, 'Eligible Mastercard gas merchant classifications only.'),
		('TRANSIT', 1.0000, 'Eligible Mastercard transit merchant classifications only.'),
		('RIDESHARE', 1.0000, 'Eligible Mastercard rideshare merchant classifications only.'),
		('EV_CHARGING', 1.0000, 'Eligible Mastercard EV charging merchant classifications only.'),
		('DINING', 0.5000, 'Standard eligible purchases.'),
		('TRAVEL', 0.5000, 'Standard eligible purchases.'),
		('OTHER', 0.5000, 'Standard eligible purchases.')
) AS r(category, rate, conditions)
WHERE c.card_name = 'RBC Cash Back Mastercard'
  AND c.issuer = 'RBC'
  AND c.is_demo = FALSE;

-- Conditions are descriptive only; the current calculator does not enforce MCC eligibility.
