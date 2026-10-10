package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.service.SelectionPolicyView;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Card JSON unchanged, plus selectionPolicy only for selectable cards. */
public class CardResponse {

    private final CreditCard card;
    private final SelectionPolicyView selectionPolicy;

    public CardResponse(CreditCard card, SelectionPolicyView selectionPolicy) {
        this.card = card;
        this.selectionPolicy = selectionPolicy;
    }

    @JsonUnwrapped
    public CreditCard getCard() {
        return card;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public SelectionPolicyView getSelectionPolicy() {
        return selectionPolicy;
    }
}
