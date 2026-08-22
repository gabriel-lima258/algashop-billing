package com.algaworks.algashop.billing.application.invoice.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LineItemOutput {
    private Integer number;
    private String name;
    private BigDecimal amount;
}