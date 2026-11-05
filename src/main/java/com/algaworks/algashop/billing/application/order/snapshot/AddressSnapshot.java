package com.algaworks.algashop.billing.application.order.snapshot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Endereco achatado, reutilizado por BillingSnapshot e ShippingSnapshot.
public record AddressSnapshot(
        @NotBlank String street,
        @NotBlank String number,
        String complement,
        @NotBlank String neighborhood,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank @Size(min = 5, max = 5) String zipCode
) {
}