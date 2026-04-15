package com.algaworks.algashop.billing.domain.model.creditcard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {
    boolean existsByIdAndCustomerId(UUID orderId, UUID customerId);
    Optional<CreditCard> findByCustomerIdAndId(UUID customerId, UUID creditCarId);
    List<CreditCard> findAllByCustomerId(UUID customerId);
}
