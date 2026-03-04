package com.algaworks.algashop.billing.domain.model.invoice;

import com.algaworks.algashop.billing.domain.model.BusinessException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class InvoiceTest {

    @Test
    void shouldIssueInvoiceCorrectly() {
        String orderId = "123";
        UUID customerId = UUID.randomUUID();
        Payer payer = InvoiceTestDataBuilder.aPayer();
        Set<LineItem> items = new HashSet<>();
        items.add(InvoiceTestDataBuilder.aLineItem());
        items.add(InvoiceTestDataBuilder.aLineItemAlt());

        Invoice invoice = Invoice.issue(orderId, customerId, payer, items);

        // calculo total feito pelos items somados
        BigDecimal expectedTotalAmount = invoice.getItems()
                .stream()
                .map(LineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Assertions.assertWith(invoice,
                i -> Assertions.assertThat(i.getId()).isNotNull(),
                i -> Assertions.assertThat(i.getTotalAmount()).isEqualTo(expectedTotalAmount),
                i -> Assertions.assertThat(i.getStatus()).isEqualTo(InvoiceStatus.UNPAID)
        );
    }

    @Test
    void shouldBeAbleMarkBillingAsPaidAndSetPaidAt() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().build();
        invoice.markAsPaid();

        Assertions.assertThat(invoice.isPaid()).isTrue();
        Assertions.assertThat(invoice.getPaidAt()).isNotNull();
    }

    @Test
    void shouldBeAbleToCancelAnInvoiceWithAReason() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().build();
        String cancelReason = "Customer requested refund";
        invoice.cancel(cancelReason);

        Assertions.assertWith(invoice,
                i -> Assertions.assertThat(i.getCancelReason()).isEqualTo(cancelReason),
                i -> Assertions.assertThat(i.getCanceledAt()).isNotNull(),
                i -> Assertions.assertThat(i.isCanceled()).isTrue()
                );
    }

    @Test
    void shouldBeAbleToChangePaymentSettings() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().build();
        UUID creditCardId = UUID.randomUUID();
        invoice.changePaymentSettings(PaymentMethod.CREDIT_CARD, creditCardId);

        Assertions.assertWith(invoice,
                i -> Assertions.assertThat(i.getPaymentSettings()).isNotNull(),
                i -> Assertions.assertThat(i.getPaymentSettings().getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD),
                i -> Assertions.assertThat(i.getPaymentSettings().getCreditCardId()).isEqualTo(creditCardId)
        );
    }

    @Test
    void shouldThrowExceptionWhenChangingPaymentSettingsToPaidInvoice() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().status(InvoiceStatus.PAID).build();
        Assertions.assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> invoice.changePaymentSettings(PaymentMethod.CREDIT_CARD, UUID.randomUUID()));
    }

    @Test
    void shouldThrowExceptionWhenTrySetInvoicePaidWhenIsCanceled() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().status(InvoiceStatus.CANCELED).build();

        Assertions.assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(invoice::markAsPaid);
    }

    @Test
    void shouldBeAbleToSetGatewayCodeOnConfigPaymentWhenIsUnpaid() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().paymentSettings(PaymentMethod.CREDIT_CARD, UUID.randomUUID()).build();
        String gatewayCode = "code-from-gateway";
        invoice.assignPaymentGatewayCode(gatewayCode);

        Assertions.assertThat(invoice.getPaymentSettings().getGatewayCode()).isEqualTo(gatewayCode);
    }

    @Test
    void shouldThrowExceptionWhenTryToSetGatewayCodeAlreadyPaid() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().status(InvoiceStatus.PAID).build();
        String gatewayCode = "code-from-gateway";

        Assertions.assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> {
                    invoice.assignPaymentGatewayCode(gatewayCode);
                });
    }

    @Test
    void shouldThrowExceptionWhenTryToCancelAnInvoiceAlreadyCanceled() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().status(InvoiceStatus.CANCELED).build();

        Assertions.assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> {
                    invoice.cancel("Refund again");
                });
    }

    @Test
    void shouldThrowExceptionWhenTryingToModifyItemsSet() {
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().build();
        Set<LineItem> items = invoice.getItems();

        Assertions.assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(items::clear);
    }

    @Test
    void shouldThrowExceptionWhenIssuingInvoiceWithEmptyItems() {
        Set<LineItem> emptyItems = new HashSet<>();
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Invoice.issue("01226N0693HDA23",
                        UUID.randomUUID(),
                        InvoiceTestDataBuilder.aPayer(),
                        emptyItems));
    }
}
