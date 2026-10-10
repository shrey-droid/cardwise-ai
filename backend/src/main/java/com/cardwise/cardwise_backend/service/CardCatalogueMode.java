package com.cardwise.cardwise_backend.service;

import com.cardwise.cardwise_backend.entity.CreditCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CardCatalogueMode {

    private final boolean demoMode;

    public CardCatalogueMode(
            @Value("${cardwise.catalogue.mode:DEMO}") String mode) {
        String normalizedMode = mode.trim().toUpperCase(Locale.ROOT);
        if (!normalizedMode.equals("DEMO") && !normalizedMode.equals("REAL")) {
            throw new IllegalArgumentException(
                    "cardwise.catalogue.mode must be DEMO or REAL"
            );
        }
        this.demoMode = normalizedMode.equals("DEMO");
    }

    public boolean includes(CreditCard card) {
        return card.isDemo() == demoMode && !card.isCatalogueWithheld();
    }

    public boolean isDemoMode() {
        return demoMode;
    }
}