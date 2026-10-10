package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.service.CardCatalogueEligibility;
import com.cardwise.cardwise_backend.service.SelectionPolicyViewService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
public class CreditCardController {

    private final CreditCardRepository repository;
    private final CardCatalogueEligibility cardCatalogueEligibility;
    private final SelectionPolicyViewService selectionPolicyViewService;

    public CreditCardController(
            CreditCardRepository repository,
            CardCatalogueEligibility cardCatalogueEligibility,
            SelectionPolicyViewService selectionPolicyViewService) {
        this.repository = repository;
        this.cardCatalogueEligibility = cardCatalogueEligibility;
        this.selectionPolicyViewService = selectionPolicyViewService;
    }

    @GetMapping
    public List<CardResponse> getAllCards(
            @RequestParam(required = false) String rewardType) {

        List<CreditCard> cards =
                (rewardType == null || rewardType.isBlank())
                        ? repository.findAll()
                        : repository.findByRewardTypeIgnoreCase(
                                rewardType.trim());

        // Policies are looked up only after the catalogue filter.
        return cards.stream()
                .filter(cardCatalogueEligibility::isEligible)
                .map(card -> new CardResponse(
                        card,
                        selectionPolicyViewService.forCard(card)
                                .orElse(null)))
                .toList();
    }
}