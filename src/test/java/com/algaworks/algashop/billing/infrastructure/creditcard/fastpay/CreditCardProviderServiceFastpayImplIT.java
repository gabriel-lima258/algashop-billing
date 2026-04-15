package com.algaworks.algashop.billing.infrastructure.creditcard.fastpay;

import com.algaworks.algashop.billing.domain.model.creditcard.LimitedCreditCard;
import com.algaworks.algashop.billing.infrastructure.AbstractFastpayImplIT;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// classe de teste para registrar, encontrar e deletar um credit card
@SpringBootTest
class CreditCardProviderServiceFastpayImplIT extends AbstractFastpayImplIT {

    @BeforeAll
    public static void beforeAll() {
        startMock();
    }

    @AfterAll
    public static void afterAll() {
        stopMock();
    }

    @Test
    void shouldRegisterCreditCard() {
        LimitedCreditCard limitedCreditCard = registerCard();

        Assertions.assertThat(limitedCreditCard.getGatewayCode()).isNotBlank();
    }

    @Test
    void shouldFindCreditCardByGatewayCode() {
        LimitedCreditCard limitedCreditCard = registerCard();
        LimitedCreditCard cardFounded = cardProviderServiceFastpay.findById(limitedCreditCard.getGatewayCode()).orElseThrow();

        Assertions.assertThat(cardFounded.getGatewayCode()).isEqualTo(limitedCreditCard.getGatewayCode());
    }

    @Test
    void shouldDeleteACreditCard() {
        LimitedCreditCard limitedCreditCard = registerCard();
        cardProviderServiceFastpay.delete(limitedCreditCard.getGatewayCode());
    }

}