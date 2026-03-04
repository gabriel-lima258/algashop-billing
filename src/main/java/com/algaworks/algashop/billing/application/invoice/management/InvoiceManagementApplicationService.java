package com.algaworks.algashop.billing.application.invoice.management;

import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardNotFoundException;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardRepository;
import com.algaworks.algashop.billing.domain.model.invoice.*;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.misc.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceManagementApplicationService {

    private final PaymentGatewayService paymentGatewayService;
    private final InvoicingService invoicingService;
    private final InvoiceRepository invoiceRepository;
    private final CreditCardRepository creditCardRepository;

    @Transactional
    public UUID generate(GenerateInvoiceInput input) {
        PaymentSettingsInput paymentSettingsInput = input.getPaymentSettings();
        verififyCreditCardId(paymentSettingsInput.getCreditCardId(), input.getCustomerId());

        Payer payer = convertToPayer(input.getPayer());

        Set<LineItem> items = convertToLineItems(input.getItems());

        Invoice invoice = invoicingService.issue(input.getOrderId(), input.getCustomerId(), payer, items);
        invoice.changePaymentSettings(paymentSettingsInput.getMethod(), paymentSettingsInput.getCreditCardId());

        invoiceRepository.saveAndFlush(invoice);

        return invoice.getId();
    }

    private Set<LineItem> convertToLineItems(Set<LineItemInput> items) {
        Set<LineItem> lineItems = new LinkedHashSet<>();
        int itemNumber = 1;
        for (LineItemInput itemInput: items) {
            lineItems.add(LineItem.builder()
                            .number(itemNumber)
                            .name(itemInput.getName())
                            .amount(itemInput.getAmount())
                    .build());
            itemNumber++;
        }

        return lineItems;
    }

    private Payer convertToPayer(PayerData payer) {
        AddressData address = payer.getAddress();
        return Payer.builder()
                .fullName(payer.getFullName())
                .email(payer.getEmail())
                .document(payer.getDocument())
                .phone(payer.getPhone())
                .address(Address.builder()
                        .city(address.getCity())
                        .state(address.getState())
                        .neighborhood(address.getNeighborhood())
                        .complement(address.getComplement())
                        .zipCode(address.getZipCode())
                        .street(address.getStreet())
                        .number(address.getNumber())
                        .build())
                .build();
    }

    private void verififyCreditCardId(UUID creditCardId, @NotNull UUID customerId) {
        if (creditCardId != null && !creditCardRepository.existsByIdAndCustomerId(creditCardId, customerId)) {
            throw new CreditCardNotFoundException();
        }
    }
}
