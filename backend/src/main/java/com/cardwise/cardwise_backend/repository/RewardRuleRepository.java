package com.cardwise.cardwise_backend.repository;

import com.cardwise.cardwise_backend.entity.RewardRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardRuleRepository
        extends JpaRepository<RewardRule, Long> {

    List<RewardRule> findByCreditCardId(Long creditCardId);
}