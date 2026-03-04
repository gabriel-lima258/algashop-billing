package com.algaworks.algashop.billing.application.invoice.management;

import com.algaworks.algashop.billing.domain.model.creditcard.CreditCard;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardRepository;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardTestDataBuilder;
import com.algaworks.algashop.billing.domain.model.invoice.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@Transactional
class InvoiceManagementApplicationServiceIT {

    @Autowired
    private InvoiceManagementApplicationService applicationService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CreditCardRepository creditCardRepository;

    // verificação caso foi chamado ou não
    @MockitoSpyBean
    private InvoicingService invoicingService;

    @Test
    public void shouldGenerateInvoiceWithCreditCardAsPayment() {
        UUID customerId = UUID.randomUUID();
        CreditCard creditCard = CreditCardTestDataBuilder.aCreditCard()
                .customerId(customerId)
                .build();
        creditCardRepository.saveAndFlush(creditCard);

        GenerateInvoiceInput input = GenerateInvoiceInputTestDataBuilder.anInput()
                .customerId(customerId)
                .build();

        input.setPaymentSettings(
                PaymentSettingsInput.builder()
                        .creditCardId(creditCard.getId())
                        .method(PaymentMethod.CREDIT_CARD)
                        .build()
        );

        UUID invoiceId = applicationService.generate(input);

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();

        // status e timestamps
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoice.getIssuedAt()).isNotNull();
        assertThat(invoice.getExpiresAt()).isNotNull();
        assertThat(invoice.getExpiresAt()).isAfter(invoice.getIssuedAt());
        assertThat(invoice.getPaidAt()).isNull();
        assertThat(invoice.getCanceledAt()).isNull();
        assertThat(invoice.getCancelReason()).isNull();

        // dados do pedido e cliente
        assertThat(invoice.getOrderId()).isEqualTo(input.getOrderId());
        assertThat(invoice.getCustomerId()).isEqualTo(customerId);

        // total calculado a partir dos itens
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));

        // itens da fatura
        assertThat(invoice.getItems()).hasSize(1);
        LineItem item = invoice.getItems().iterator().next();
        assertThat(item.getName()).isEqualTo("Product 1");
        assertThat(item.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(item.getNumber()).isEqualTo(1);

        // dados do pagador
        Payer payer = invoice.getPayer();
        assertThat(payer).isNotNull();
        assertThat(payer.getFullName()).isEqualTo("John Doe");
        assertThat(payer.getDocument()).isEqualTo("111.222.333-44");
        assertThat(payer.getPhone()).isEqualTo("11-99999-8888");
        assertThat(payer.getEmail()).isEqualTo("john.doe@email.com");

        // endereço do pagador
        Address address = payer.getAddress();
        assertThat(address).isNotNull();
        assertThat(address.getStreet()).isEqualTo("Street Name");
        assertThat(address.getNumber()).isEqualTo("123");
        assertThat(address.getComplement()).isEqualTo("Apt 1");
        assertThat(address.getNeighborhood()).isEqualTo("Neighborhood");
        assertThat(address.getCity()).isEqualTo("City");
        assertThat(address.getState()).isEqualTo("State");
        assertThat(address.getZipCode()).isEqualTo("12345-678");

        // configurações de pagamento
        PaymentSettings paymentSettings = invoice.getPaymentSettings();
        assertThat(paymentSettings).isNotNull();
        assertThat(paymentSettings.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(paymentSettings.getCreditCardId()).isEqualTo(creditCard.getId());
        assertThat(paymentSettings.getGatewayCode()).isNull();

        // verificação de chamada ao serviço de emissão
        Mockito.verify(invoicingService).issue(any(), any(), any(), any());
    }

    @Test
    public void shouldGenerateInvoiceWithGatewayBalanceAsPayment() {
        UUID customerId = UUID.randomUUID();
        CreditCard creditCard = CreditCardTestDataBuilder.aCreditCard()
                .customerId(customerId)
                .build();
        creditCardRepository.saveAndFlush(creditCard);

        GenerateInvoiceInput input = GenerateInvoiceInputTestDataBuilder.anInput()
                .customerId(customerId)
                .build();

        input.setPaymentSettings(
                PaymentSettingsInput.builder()
                        .creditCardId(creditCard.getId())
                        .method(PaymentMethod.GATEWAY_BALANCE)
                        .build()
        );

        UUID invoiceId = applicationService.generate(input);

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();

        // status e timestamps
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoice.getIssuedAt()).isNotNull();
        assertThat(invoice.getExpiresAt()).isNotNull();
        assertThat(invoice.getPaidAt()).isNull();
        assertThat(invoice.getCanceledAt()).isNull();

        // dados do pedido e cliente
        assertThat(invoice.getOrderId()).isEqualTo(input.getOrderId());
        assertThat(invoice.getCustomerId()).isEqualTo(customerId);

        // total calculado
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));

        // configurações de pagamento com GATEWAY_BALANCE
        PaymentSettings paymentSettings = invoice.getPaymentSettings();
        assertThat(paymentSettings).isNotNull();
        assertThat(paymentSettings.getPaymentMethod()).isEqualTo(PaymentMethod.GATEWAY_BALANCE);
        assertThat(paymentSettings.getCreditCardId()).isEqualTo(creditCard.getId());

        Mockito.verify(invoicingService).issue(any(), any(), any(), any());
    }

}