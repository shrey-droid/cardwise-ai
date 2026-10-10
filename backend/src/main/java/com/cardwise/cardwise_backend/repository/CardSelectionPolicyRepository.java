package com.cardwise.cardwise_backend.repository;

import com.cardwise.cardwise_backend.entity.CardSelectionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardSelectionPolicyRepository
        extends JpaRepository<CardSelectionPolicy, Long> {
}
