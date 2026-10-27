package com.algaworks.algashop.billing.application.invoice.management;

import com.algaworks.algashop.billing.application.order.event.OrderPlacedIntegrationEvent;
import com.algaworks.algashop.billing.application.order.snapshot.AddressSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.BillingSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.OrderItemSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.PaymentSnapshot;
import com.algaworks.algashop.billing.domain.model.invoice.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class GenerateInvoiceInputAssembler {

    private static final String SHIPPING_LINE_ITEM_NAME = "Shipping";

    public GenerateInvoiceInput toGenerateInvoiceInput(OrderPlacedIntegrationEvent event) {
        PaymentSettingsInput paymentSettings = toPaymentSettings(event.getPayment());
        List<LineItemInput> lineItems = toLineItems(event);

        return GenerateInvoiceInput.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .paymentSettings(paymentSettings)
                .payer(toPayer(event.getBilling()))
                .items(lineItems)
                .build();
    }

    private PaymentSettingsInput toPaymentSettings(PaymentSnapshot payment) {
        PaymentMethod method = PaymentMethod.valueOf(payment.method());
        return PaymentSettingsInput.builder()
                .method(method)
                .creditCardId(payment.creditCardId())
                .build();
    }

    private List<LineItemInput> toLineItems(OrderPlacedIntegrationEvent event) {
        List<LineItemInput> lineItems = new ArrayList<>(event.getItems().size() + 1);
        event.getItems().stream()
                .map(this::toLineItem)
                .forEach(lineItems::add);

        if (event.getShipping().cost().compareTo(BigDecimal.ZERO) > 0) {
            lineItems.add(LineItemInput.builder()
                    .name(SHIPPING_LINE_ITEM_NAME)
                    .amount(event.getShipping().cost())
                    .build());
        }

        return lineItems;
    }

    private LineItemInput toLineItem(OrderItemSnapshot item) {
        return LineItemInput.builder()
                .name(item.productName() + " - x" + item.quantity())
                .amount(item.totalAmount())
                .build();
    }

    private PayerData toPayer(BillingSnapshot billing) {
        return PayerData.builder()
                .fullName("%s %s".formatted(billing.firstName(), billing.lastName()))
                .document(billing.document())
                .email(billing.email())
                .phone(billing.phone())
                .address(toPayerAddress(billing.address()))
                .build();
    }

    private AddressData toPayerAddress(
            AddressSnapshot address) {
        return AddressData.builder()
                .street(address.street())
                .number(address.number())
                .complement(address.complement())
                .neighborhood(address.neighborhood())
                .city(address.city())
                .state(address.state())
                .zipCode(address.zipCode())
                .build();
    }
}