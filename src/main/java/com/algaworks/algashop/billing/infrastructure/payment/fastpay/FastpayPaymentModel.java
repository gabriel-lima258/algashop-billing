package com.algaworks.algashop.billing.infrastructure.payment.fastpay;

import lombok.Data;

import java.math.BigDecimal;

// data de resposta da api fastpay
@Data
public class FastpayPaymentModel {
    private String id;
    private String referenceCode;
    private String status;
    private String method;
    private BigDecimal totalAmount;
}
