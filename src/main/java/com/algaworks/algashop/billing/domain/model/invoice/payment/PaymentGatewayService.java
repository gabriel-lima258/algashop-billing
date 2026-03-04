package com.algaworks.algashop.billing.domain.model.invoice.payment;

// gateway ACL para serviços de pagamentos terceiros
public interface PaymentGatewayService {
    Payment capture(PaymentRequest request);
    Payment findByCode(String gatewayCode);

}
