package com.cardwise.cardwise_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "card_selection_policies")
public class CardSelectionPolicy {

    @Id
    @Column(name = "credit_card_id")
    private Long creditCardId;

    @Column(name = "base_selection_limit", nullable = false)
    private Integer baseSelectionLimit;

    @Column(name = "extended_selection_limit", nullable = false)
    private Integer extendedSelectionLimit;

    @Column(name = "extended_requirement", length = 100)
    private String extendedRequirement;

    // Informational only; category-change history is not tracked.
    @Column(name = "change_hold_days")
    private Integer changeHoldDays;

    public CardSelectionPolicy() {}

    public Long getCreditCardId() {
        return creditCardId;
    }

    public void setCreditCardId(Long creditCardId) {
        this.creditCardId = creditCardId;
    }

    public Integer getBaseSelectionLimit() {
        return baseSelectionLimit;
    }

    public void setBaseSelectionLimit(Integer baseSelectionLimit) {
        this.baseSelectionLimit = baseSelectionLimit;
    }

    public Integer getExtendedSelectionLimit() {
        return extendedSelectionLimit;
    }

    public void setExtendedSelectionLimit(Integer extendedSelectionLimit) {
        this.extendedSelectionLimit = extendedSelectionLimit;
    }

    public String getExtendedRequirement() {
        return extendedRequirement;
    }

    public void setExtendedRequirement(String extendedRequirement) {
        this.extendedRequirement = extendedRequirement;
    }

    public Integer getChangeHoldDays() {
        return changeHoldDays;
    }

    public void setChangeHoldDays(Integer changeHoldDays) {
        this.changeHoldDays = changeHoldDays;
    }
}
