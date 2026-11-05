package com.algaworks.algashop.billing.application.order.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

// Item do OrderSnapshot: produto, preco e quantidade ja fechados no checkout.
// O orderId nao vem aqui - e reinjetado pelo assembler a partir do snapshot pai.
public record OrderItemSnapshot(
        @NotBlank String id,
        @NotBlank String orderItemId,
        @NotNull UUID productId,
        @NotBlank String productName,
        @NotNull @Positive BigDecimal price,
        @NotNull @Positive Integer quantity,
        @NotNull @Positive BigDecimal totalAmount
) {
}
