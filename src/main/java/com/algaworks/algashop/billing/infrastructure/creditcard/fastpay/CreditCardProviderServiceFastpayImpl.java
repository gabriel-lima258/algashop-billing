package com.algaworks.algashop.billing.infrastructure.creditcard.fastpay;

import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardProviderService;
import com.algaworks.algashop.billing.domain.model.creditcard.LimitedCreditCard;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// So ADAPTA: implementa a porta do dominio e traduz o DTO do Fastpay para o modelo de
// cartao. Resiliencia e traducao de erro moram no ResilientFastpayCreditCardAPIClient.
//
// Antes esta classe falava direto com o FastpayCreditCardAPIClient, entao a excecao crua do
// RestClient subia ate o @ExceptionHandler(Exception.class) e virava 500 - onde o certo era
// 502/504. E nao havia circuito nenhum protegendo o cadastro de cartao.
@Service
@ConditionalOnProperty(name = "algashop.integrations.payment.provider", havingValue = "FASTPAY")
@RequiredArgsConstructor
public class CreditCardProviderServiceFastpayImpl implements CreditCardProviderService {

    private final ResilientFastpayCreditCardAPIClient fastpayCreditCardAPIClient;

    @Override
    public LimitedCreditCard register(UUID customerId, String tokenizedCard) {
        FastpayCreditCardInput input = FastpayCreditCardInput.builder()
                .tokenizedCard(tokenizedCard)
                .customerCode(customerId.toString())
                .build();

        return toLimitedCreditCard(fastpayCreditCardAPIClient.create(input));
    }

    @Override
    public Optional<LimitedCreditCard> findById(String gatewayCode) {
        // o 404 vira Optional.empty() dentro do cliente resiliente
        return fastpayCreditCardAPIClient.findById(gatewayCode)
                .map(this::toLimitedCreditCard);
    }

    @Override
    public void delete(String gatewayCode) {
        fastpayCreditCardAPIClient.delete(gatewayCode);
    }

    private LimitedCreditCard toLimitedCreditCard(FastpayCreditCardResponse response) {
        return LimitedCreditCard.builder()
                .brand(response.getBrand())
                .expYear(response.getExpYear())
                .expMonth(response.getExpMonth())
                .lastNumbers(response.getLastNumbers())
                .gatewayCode(response.getId())
                .build();
    }
}
