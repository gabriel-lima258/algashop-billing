package com.algaworks.algashop.billing.infrastructure.payment.fastpay;

import com.algaworks.algashop.billing.domain.model.creditcard.CreditCard;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardNotFoundException;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardRepository;
import com.algaworks.algashop.billing.domain.model.invoice.Address;
import com.algaworks.algashop.billing.domain.model.invoice.Payer;
import com.algaworks.algashop.billing.domain.model.invoice.payment.Payment;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentGatewayService;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentRequest;
import com.algaworks.algashop.billing.infrastructure.creditcard.fastpay.FastpayCreditCardAPIClient;
import com.algaworks.algashop.billing.infrastructure.payment.AlgaShopPaymentProperties;
import com.algaworks.algashop.billing.presentation.BadGatewayException;
import com.algaworks.algashop.billing.presentation.GatewayTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "algashop.integrations.payment.provider", havingValue = "FASTPAY")
@RequiredArgsConstructor
public class PaymentGatewayServiceFastpayImpl implements PaymentGatewayService {

    private final FastpayPaymentAPIClient fastpayPaymentAPIClient;
    private final CreditCardRepository creditCardRepository;

    private final AlgaShopPaymentProperties algaShopPaymentProperties;

    @Override
    public Payment capture(PaymentRequest request) {
        log.info("Capturing payment via Fastpay for invoice {}", request.getInvoiceId());
        try {
            FastpayPaymentInput input = convertToInput(request);
            FastpayPaymentModel response = fastpayPaymentAPIClient.capture(input);
            return convertToPayment(response);
        } catch (ResourceAccessException e) {
            throw new GatewayTimeoutException("Fastpay gateway is unavailable", e);
        } catch (HttpServerErrorException e) {
            throw new BadGatewayException("Fastpay gateway returned an error", e);
        }
    }

    @Override
    public Payment findByCode(String gatewayCode) {
        try {
            FastpayPaymentModel response = fastpayPaymentAPIClient.findById(gatewayCode);
            return convertToPayment(response);
        } catch (ResourceAccessException e) {
            throw new GatewayTimeoutException("Fastpay gateway is unavailable", e);
        } catch (HttpServerErrorException e) {
            throw new BadGatewayException("Fastpay gateway returned an error", e);
        }
    }

    private FastpayPaymentInput convertToInput(PaymentRequest request) {
        Payer payer = request.getPayer();
        Address address = payer.getAddress();

        var builder = FastpayPaymentInput.builder()
                .totalAmount(request.getAmount())
                .referenceCode(request.getInvoiceId().toString())
                .fullName(payer.getFullName())
                .document(payer.getDocument())
                .phone(payer.getPhone())
                .zipCode(address.getZipCode())
                .addressLine1(address.getState() + ", " + address.getNumber())
                .addressLine2(address.getComplement())
                .replyToUrl(algaShopPaymentProperties.getFastpay().getWebhookUrl());

        // setando o metodo de pagamento
        switch (request.getMethod()) {
            case CREDIT_CARD -> {
                builder.method(FastpayPaymentMethod.CREDIT.name());
                // procura o cartao cadastrado
                CreditCard creditCard = creditCardRepository.findById(request.getCreditCardId())
                        .orElseThrow(() -> new CreditCardNotFoundException());
                // retorna o gateway code do cartao da api
                builder.creditCardId(creditCard.getGatewayCode());
            }
            case GATEWAY_BALANCE -> {
                builder.method(FastpayPaymentMethod.GATEWAY_BALANCE.name());
            }
        }

        return builder.build();
    }

    // converte a resposta para o domain
    private Payment convertToPayment(FastpayPaymentModel response) {
        var builder = Payment.builder()
                .gatewayCode(response.getId())
                .invoiceId(UUID.fromString(response.getReferenceCode()));

        FastpayPaymentMethod fastpayPaymentMethod;

        // tentativa de tipo de pagamento, caso não reconheça o tipo lança exception quando não existe em domain
        try {
            fastpayPaymentMethod = FastpayPaymentMethod.valueOf(response.getMethod());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown payment method " + response.getMethod());
        }

        // tentativa de status de pagamento, caso não reconheça o tipo lança exception quando não existe em domain
        FastpayPaymentStatus fastpayPaymentStatus;

        try {
            fastpayPaymentStatus = FastpayPaymentStatus.valueOf(response.getStatus());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown payment status " + response.getStatus());
        }

        builder.method(FastpayEnumConverter.convert(fastpayPaymentMethod));
        builder.status(FastpayEnumConverter.convert(fastpayPaymentStatus));

        return builder.build();
    }
}
