package com.algaworks.algashop.billing.application.order.snapshot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

// Dados de entrega do OrderSnapshot: custo e data ja calculados no checkout, mais
// destinatario e endereco - o consumidor nao volta a chamar o servico de frete.
public record ShippingSnapshot(
        @NotNull @PositiveOrZero BigDecimal cost,
        @NotNull LocalDate expectedDate,
        @NotNull @Valid RecipientSnapshot recipient,
        @NotNull @Valid AddressSnapshot address
) {
}
