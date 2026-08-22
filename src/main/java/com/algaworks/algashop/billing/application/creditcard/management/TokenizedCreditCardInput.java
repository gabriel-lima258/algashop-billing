package com.algaworks.algashop.billing.application.creditcard.management;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class TokenizedCreditCardInput {

    // preenchido pelo controller com o sub do token (recurso /me) - nao vem do body,
    // e nao pode ter @NotNull: o @Valid roda ANTES de o controller setar o valor
    @JsonIgnore
    private UUID customerId;

    @NotBlank
    private String tokenizedCard;
}
