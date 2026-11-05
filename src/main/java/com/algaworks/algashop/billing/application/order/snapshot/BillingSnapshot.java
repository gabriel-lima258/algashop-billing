package com.algaworks.algashop.billing.application.order.snapshot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Dados de cobranca do OrderSnapshot: FullName/Document/Phone/Email achatados em String.
public record BillingSnapshot(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String document,
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotNull @Valid AddressSnapshot address
) {
}
