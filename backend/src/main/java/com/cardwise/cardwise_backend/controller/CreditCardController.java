package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.service.CardCatalogueMode;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
public class CreditCardController {

    private final CreditCardRepository repository;
    private final CardCatalogueMode cardCatalogueMode;

    public CreditCardController(
            CreditCardRepository repository,
            CardCatalogueMode cardCatalogueMode) {
        this.repository = repository;
        this.cardCatalogueMode = cardCatalogueMode;
    }

    @GetMapping
    public List<CreditCard> getAllCards(
            @RequestParam(required = false) String rewardType) {

        if (rewardType == null || rewardType.isBlank()) {
            return repository.findAll().stream()
                .filter(cardCatalogueMode::includes)
                .toList();
        }

        return repository.findByRewardTypeIgnoreCase(
                rewardType.trim()
            ).stream()
            .filter(cardCatalogueMode::includes)
            .toList();
    }
}