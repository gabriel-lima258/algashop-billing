package com.algaworks.algashop.billing.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

// Espelho LOCAL do contrato minimo dos eventos de integracao do product-catalog.
// No PRODUTOR o getAggregateId() vira a KEY do record; aqui, no consumidor, ninguem
// chama este metodo - a key chega pronta no header KafkaHeaders.RECEIVED_KEY. A
// interface existe para a copia dos eventos ficar simetrica ao contrato publicado
// (mesma forma, mesmo wire format), nao porque o ordering decida particao de alguem.
public interface IntegrationEvent {
    // @JsonIgnore espelha o produtor: aggregateId fora do JSON nos dois lados - o id
    // ja viaja como key do record e como campo productId do payload
    @JsonIgnore
    String getAggregateId();

    // retorna null em casos que não precisa de idempotency
    @JsonIgnore
    default UUID getIdempotencyKey() {
        return null;
    }
}
