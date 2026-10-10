package com.cardwise.cardwise_backend.controller;

import com.cardwise.cardwise_backend.entity.CreditCard;
import com.cardwise.cardwise_backend.repository.CreditCardRepository;
import com.cardwise.cardwise_backend.service.CardCatalogueEligibility;
import com.cardwise.cardwise_backend.service.SelectionPolicyViewService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreditCardController.class)
class CreditCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreditCardRepository repository;

    @MockitoBean
    private CardCatalogueEligibility cardCatalogueEligibility;

    @MockitoBean
    private SelectionPolicyViewService selectionPolicyViewService;

    @Test
    void shouldReturnAllCardsWithoutFilter() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(repository).findAll();
    }

    @Test
    void shouldOnlyReturnCardsInTheActiveCatalogueMode() throws Exception {
        CreditCard demoCard = new CreditCard();
        demoCard.setDemo(true);
        CreditCard realCard = new CreditCard();
        realCard.setDemo(false);

        when(repository.findAll()).thenReturn(List.of(demoCard, realCard));
        when(cardCatalogueEligibility.isEligible(demoCard)).thenReturn(false);
        when(cardCatalogueEligibility.isEligible(realCard)).thenReturn(true);

        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].demo").value(false));
    }

    @Test
    void shouldFilterCashbackCards() throws Exception {
        when(repository.findByRewardTypeIgnoreCase("CASHBACK"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cards")
                        .param("rewardType", "CASHBACK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(repository)
            .findByRewardTypeIgnoreCase("CASHBACK");
        }

        @Test
        void shouldAcceptLowercaseRewardType() throws Exception {
        when(repository.findByRewardTypeIgnoreCase("cashback"))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cards")
                .param("rewardType", "cashback"))
            .andExpect(status().isOk());

        verify(repository)
            .findByRewardTypeIgnoreCase("cashback");
        }

        @Test
        void shouldTrimRewardType() throws Exception {
        when(repository.findByRewardTypeIgnoreCase("CASHBACK"))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cards")
                .param("rewardType", "  CASHBACK  "))
            .andExpect(status().isOk());

        verify(repository)
            .findByRewardTypeIgnoreCase("CASHBACK");
        }

        @Test
        void shouldReturnAllCardsForBlankRewardType() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/cards")
                .param("rewardType", "   "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        verify(repository).findAll();
    }

    @Test
    void shouldNotLookUpPoliciesForCardsOutsideTheCatalogue()
            throws Exception {
        CreditCard withheld = new CreditCard();
        CreditCard visible = new CreditCard();

        when(repository.findAll()).thenReturn(List.of(withheld, visible));
        when(cardCatalogueEligibility.isEligible(withheld)).thenReturn(false);
        when(cardCatalogueEligibility.isEligible(visible)).thenReturn(true);

        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(selectionPolicyViewService).forCard(visible);
        verify(selectionPolicyViewService, never()).forCard(withheld);
    }
}
