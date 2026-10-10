-- Activate the Tangerine Money-Back Credit Card in the catalogue.
--
-- Applying this migration makes the card visible wherever it runs. It first
-- verifies every prerequisite and raises an exception otherwise, which makes
-- Flyway fail and roll back the transaction. Rates are stored as percentages
-- (2.0000 = 2%), matching V9-V11.

DO $$
DECLARE
    v_card_id BIGINT;
    v_count INTEGER;
    v_updated INTEGER;
BEGIN
    SELECT count(*) INTO v_count
    FROM credit_cards
    WHERE issuer = 'Tangerine'
      AND card_name = 'Tangerine Money-Back Credit Card'
      AND is_demo = FALSE;

    IF v_count <> 1 THEN
        RAISE EXCEPTION
            'V12 activation blocked: expected exactly one Tangerine Money-Back Credit Card, found %',
            v_count;
    END IF;

    SELECT id INTO v_card_id
    FROM credit_cards
    WHERE issuer = 'Tangerine'
      AND card_name = 'Tangerine Money-Back Credit Card'
      AND is_demo = FALSE;

    IF NOT EXISTS (
        SELECT 1 FROM credit_cards
        WHERE id = v_card_id
          AND reward_type = 'CASHBACK'
          AND annual_fee = 0
    ) THEN
        RAISE EXCEPTION
            'V12 activation blocked: card % must be a zero-fee CASHBACK card', v_card_id;
    END IF;

    SELECT count(*) INTO v_count
    FROM card_selection_policies
    WHERE credit_card_id = v_card_id
      AND base_selection_limit = 2
      AND extended_selection_limit = 3
      AND change_hold_days = 90
      AND extended_requirement = 'CASHBACK_DEPOSITED_TO_TANGERINE_SAVINGS_ACCOUNT';

    IF v_count <> 1 THEN
        RAISE EXCEPTION
            'V12 activation blocked: expected one selection policy (2/3, 90 days, savings-account requirement) for card %, found %',
            v_card_id, v_count;
    END IF;

    SELECT count(*) INTO v_count
    FROM reward_rules
    WHERE credit_card_id = v_card_id;

    IF v_count <> 8 THEN
        RAISE EXCEPTION
            'V12 activation blocked: expected 8 reward rules for card %, found %',
            v_card_id, v_count;
    END IF;

    SELECT count(*) INTO v_count
    FROM reward_rules
    WHERE credit_card_id = v_card_id
      AND spending_category IN ('GROCERIES', 'GAS', 'DINING', 'TRANSIT')
      AND is_selectable = TRUE
      AND reward_rate = 2.0000
      AND unselected_reward_rate = 0.5000
      AND spending_cap IS NULL;

    IF v_count <> 4 THEN
        RAISE EXCEPTION
            'V12 activation blocked: expected 4 selectable rules at 2%% selected / 0.5%% unselected for card %, found %',
            v_card_id, v_count;
    END IF;

    SELECT count(*) INTO v_count
    FROM reward_rules
    WHERE credit_card_id = v_card_id
      AND spending_category IN ('TRAVEL', 'RIDESHARE', 'EV_CHARGING', 'OTHER')
      AND is_selectable = FALSE
      AND reward_rate = 0.5000
      AND unselected_reward_rate IS NULL
      AND spending_cap IS NULL;

    IF v_count <> 4 THEN
        RAISE EXCEPTION
            'V12 activation blocked: expected 4 fixed rules at 0.5%% for card %, found %',
            v_card_id, v_count;
    END IF;

    UPDATE credit_cards
    SET catalogue_withheld = FALSE
    WHERE id = v_card_id
      AND issuer = 'Tangerine'
      AND card_name = 'Tangerine Money-Back Credit Card'
      AND is_demo = FALSE;

    GET DIAGNOSTICS v_updated = ROW_COUNT;

    IF v_updated <> 1 THEN
        RAISE EXCEPTION
            'V12 activation blocked: expected to update exactly one card, updated %',
            v_updated;
    END IF;
END
$$;
