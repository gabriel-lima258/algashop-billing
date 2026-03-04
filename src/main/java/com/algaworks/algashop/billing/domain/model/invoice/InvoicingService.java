package com.algaworks.algashop.billing.domain.model.invoice;

import com.algaworks.algashop.billing.domain.model.BusinessException;
import com.algaworks.algashop.billing.domain.model.invoice.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoicingService {

    private final InvoiceRepository invoiceRepository;

    public Invoice issue(String orderId, UUID customerId, Payer payer, Set<LineItem> items) {
        if (invoiceRepository.existsByOrderId(orderId)) {
            throw new BusinessException(String.format("Invoice already exists for order %s", orderId));
        }
        return Invoice.issue(orderId, customerId, payer, items);
    }

    // atualizar o processamento de pagamento
    public void assignPayment(Invoice invoice, Payment payment) {
        invoice.assignPaymentGatewayCode(payment.getGatewayCode());
        switch (payment.getStatus()) {
            case FAILED -> {
                invoice.cancel("Payment failed");
            }
            case REFUNDED -> {
                invoice.cancel("Payment refunded");
            }
            case PAID -> {
                invoice.markAsPaid();
            }
        }
    }
}
