package com.cardwise.cardwise_backend.repository;

import com.cardwise.cardwise_backend.entity.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

	List<CreditCard> findByRewardTypeIgnoreCase(String rewardType);
}