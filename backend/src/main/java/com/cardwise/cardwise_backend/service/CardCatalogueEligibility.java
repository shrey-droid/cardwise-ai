package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CreditCard;

import org.springframework.stereotype.Service;

/**
 * Single rule for whether a card may appear in the catalogue, recommendations,
 * simulation and break-even: it must be in the active catalogue mode, not
 * withheld, and, if it has customizable categories, fully configurable.
 */
@Service
public class CardCatalogueEligibility {

    private final CardCatalogueMode cardCatalogueMode;
    private final SelectionPolicyViewService selectionPolicyViewService;

    public CardCatalogueEligibility(
            CardCatalogueMode cardCatalogueMode,
            SelectionPolicyViewService selectionPolicyViewService) {
        this.cardCatalogueMode = cardCatalogueMode;
        this.selectionPolicyViewService = selectionPolicyViewService;
    }

    public boolean isEligible(CreditCard card) {
        // Checked first so withheld cards never trigger policy lookups.
        if (!cardCatalogueMode.includes(card)) {
            return false;
        }

        return selectionPolicyViewService.evaluate(card).status()
                != SelectionPolicyViewService.Status.INVALID;
    }
}
