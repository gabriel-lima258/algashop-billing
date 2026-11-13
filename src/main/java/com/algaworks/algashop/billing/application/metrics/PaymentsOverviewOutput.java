package com.algaworks.algashop.billing.application.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;

// Uma linha por mes e forma de pagamento. O YearMonth sai no JSON como "2026-07".
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentsOverviewOutput {
    private YearMonth yearMonth;
    private String paymentMethod;
    private Integer quantity;
    private BigDecimal totalAmount;
}
