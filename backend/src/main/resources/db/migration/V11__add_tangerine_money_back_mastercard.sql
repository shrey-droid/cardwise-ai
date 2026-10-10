-- Tangerine Money-Back Credit Card
-- Terms checked against the official product page on 2026-10-10.
-- Source: https://www.tangerine.ca/en/personal/spend/credit-cards/money-back-credit-card
--
-- Official terms:
--   * 2% cash back in 2 categories chosen by the cardholder, or 3 when the
--     cash back is deposited to a Tangerine Savings Account.
--   * 0.5% on everything else. No cap is stated ("unlimited").
--   * Categories can be changed once after account opening; later changes
--     take effect on the first billing date after a 90-day hold.
--   * Selectable categories (13): Grocery, Restaurants, Gas, Entertainment,
--     Furniture, Hotel-Motel, Drug Store, Recurring Bill Payments, Home
--     Improvement, Public Transportation and Parking, E-Games, Fitness and
--     Sports Clubs, Foreign Currency Spend.
--
-- Limitations of the CardWise spending model:
--   * Only GROCERIES, GAS, DINING and TRANSIT map to Tangerine categories.
--   * The other 9 Tangerine categories (and hotel stays, drug stores,
--     recurring bills, etc.) have no CardWise category. Spending there is
--     entered as OTHER and is rated at 0.5%, which understates Tangerine.
--   * TRANSIT is an estimate for "Public Transportation and Parking" and
--     depends on merchant classification; not all transit spending qualifies.
--   * Gas qualifies only at merchants classified as "Service Stations".
--   * The 90-day category-change hold is informational; changes are not tracked.
--
-- Do not rely on this card in recommendations until RecommendationService
-- applies RewardRuleResolver. Until then it is withheld from the catalogue.

ALTER TABLE credit_cards
    ADD COLUMN catalogue_withheld BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO credit_cards (
	card_name,
	issuer,
	reward_type,
	annual_fee,
	is_demo,
	catalogue_withheld,
	official_url,
	last_verified_at
)
VALUES (
	'Tangerine Money-Back Credit Card',
	'Tangerine',
	'CASHBACK',
	0.00,
	FALSE,
	TRUE,
	'https://www.tangerine.ca/en/personal/spend/credit-cards/money-back-credit-card',
	DATE '2026-10-10'
);

INSERT INTO card_selection_policies (
	credit_card_id,
	base_selection_limit,
	extended_selection_limit,
	extended_requirement,
	change_hold_days
)
SELECT
	c.id,
	2,
	3,
	'CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT',
	90
FROM credit_cards c
WHERE c.card_name = 'Tangerine Money-Back Credit Card'
  AND c.issuer = 'Tangerine'
  AND c.is_demo = FALSE;

INSERT INTO reward_rules (
	credit_card_id,
	spending_category,
	reward_rate,
	is_selectable,
	unselected_reward_rate,
	conditions
)
SELECT
	c.id,
	r.category,
	r.rate,
	r.selectable,
	r.unselected_rate,
	r.conditions
FROM credit_cards c
CROSS JOIN (
	VALUES
		('GROCERIES', 2.0000, TRUE, 0.5000, 'Applies only when selected as a 2% category. Grocery merchant classification.'),
		('GAS', 2.0000, TRUE, 0.5000, 'Applies only when selected as a 2% category. Service Stations merchant classification only.'),
		('DINING', 2.0000, TRUE, 0.5000, 'Applies only when selected as a 2% category. Tangerine category: Restaurants.'),
		('TRANSIT', 2.0000, TRUE, 0.5000, 'Estimate. Applies only when Public Transportation and Parking is selected; subject to merchant classification, so not all transit spending qualifies.'),
		('TRAVEL', 0.5000, FALSE, NULL, 'Standard eligible purchases. Hotel-Motel is a Tangerine selectable category but is not modelled here.'),
		('RIDESHARE', 0.5000, FALSE, NULL, 'Standard eligible purchases.'),
		('EV_CHARGING', 0.5000, FALSE, NULL, 'Standard eligible purchases.'),
		('OTHER', 0.5000, FALSE, NULL, 'Standard eligible purchases. Other Tangerine 2% categories are not modelled, so this may understate Tangerine.')
) AS r(category, rate, selectable, unselected_rate, conditions)
WHERE c.card_name = 'Tangerine Money-Back Credit Card'
  AND c.issuer = 'Tangerine'
  AND c.is_demo = FALSE;
