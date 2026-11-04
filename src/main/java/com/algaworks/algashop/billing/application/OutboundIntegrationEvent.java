package com.algaworks.algashop.billing.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

// Contrato dos eventos de integracao que o billing PUBLICA. O getAggregateId() vira a
// KEY do record: todos os eventos de uma mesma fatura caem na mesma particao, em ordem.
public interface OutboundIntegrationEvent {
    // @JsonIgnore: o id ja viaja como key do record e como campo invoiceId do payload
    @JsonIgnore
    String getAggregateId();

    // retorna null em casos que não precisa de idempotency
    @JsonIgnore
    default UUID getIdempotencyKey() {
        return null;
    }
}
