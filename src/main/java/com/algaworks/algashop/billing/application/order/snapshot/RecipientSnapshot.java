package com.algaworks.algashop.billing.application.order.snapshot;

import jakarta.validation.constraints.NotBlank;

// Destinatario da entrega (parte do ShippingSnapshot), sem endereco - esse fica no pai.
public record RecipientSnapshot(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String document,
        @NotBlank String phone
) {
}