package com.algaworks.algashop.billing.application.invoice.query;

import com.algaworks.algashop.billing.application.invoice.AbstractApplicationTest;
import com.algaworks.algashop.billing.domain.model.invoice.Invoice;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceNotFoundException;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceRepository;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceStatus;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceTestDataBuilder;
import com.algaworks.algashop.billing.domain.model.invoice.PaymentMethod;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

class InvoiceQueryServiceIT extends AbstractApplicationTest {

    @Autowired
    private InvoiceQueryService invoiceQueryService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Test
    void shouldFindByOrderId() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().build();
        invoice.changePaymentSettings(PaymentMethod.GATEWAY_BALANCE, null);
        invoiceRepository.saveAndFlush(invoice);
        InvoiceOutput invoiceOutput = invoiceQueryService.findByOrderId(invoice.getOrderId());

        Assertions.assertThat(invoiceOutput.getId()).isEqualTo(invoice.getId());
    }

    // o caminho do recurso /me: alem de encontrar, valida os campos novos do contrato
    // (items, gatewayCode) e o mapeamento paymentMethod -> method do ModelMapper STRICT
    @Test
    void shouldFindByOrderIdAndCustomerId() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice()
                .orderId("01226N0693HDB")
                .paymentSettings(PaymentMethod.GATEWAY_BALANCE, null)
                .gatewayCode("GW-328982")
                .build();
        invoiceRepository.saveAndFlush(invoice);

        InvoiceOutput invoiceOutput = invoiceQueryService
                .findByOrderIdAndCustomerId(invoice.getOrderId(), invoice.getCustomerId());

        Assertions.assertThat(invoiceOutput.getId()).isEqualTo(invoice.getId());
        Assertions.assertThat(invoiceOutput.getCustomerId()).isEqualTo(invoice.getCustomerId());
        Assertions.assertThat(invoiceOutput.getItems()).isNotEmpty();
        Assertions.assertThat(invoiceOutput.getPaymentSettings().getGatewayCode()).isEqualTo("GW-328982");
        Assertions.assertThat(invoiceOutput.getPaymentSettings().getMethod())
                .isEqualTo(PaymentMethod.GATEWAY_BALANCE);
    }

    // isolamento entre clientes: a fatura existe, mas pertence a outro customerId -
    // o filtro na consulta faz o resultado ser identico a "nao encontrado"
    @Test
    void shouldNotFindInvoiceBelongingToAnotherCustomer() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice()
                .orderId("01226N0693HDC")
                .build();
        invoiceRepository.saveAndFlush(invoice);

        UUID anotherCustomerId = UUID.randomUUID();

        Assertions.assertThatExceptionOfType(InvoiceNotFoundException.class)
                .isThrownBy(() -> invoiceQueryService
                        .findByOrderIdAndCustomerId(invoice.getOrderId(), anotherCustomerId));
    }

    @Test
    void shouldExposeCancelReason() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice()
                .orderId("01226N0693HDD")
                .status(InvoiceStatus.CANCELED)
                .cancelReason("Payment refunded")
                .build();
        invoiceRepository.saveAndFlush(invoice);

        InvoiceOutput invoiceOutput = invoiceQueryService
                .findByOrderIdAndCustomerId(invoice.getOrderId(), invoice.getCustomerId());

        Assertions.assertThat(invoiceOutput.getCancelReason()).isEqualTo("Payment refunded");
    }

}