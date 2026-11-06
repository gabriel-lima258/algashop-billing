package com.algaworks.algashop.billing.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

// Contrato das respostas (replies) que o billing envia ao orquestrador. Diferente do
// evento, a resposta tem destinatario: vai para o topico que o comando indicou. O
// getAggregateId() vira a KEY do record - o orderId, a mesma key do comando que ela
// responde, para comando e resposta do mesmo pedido ficarem na mesma particao.
public interface OutboundIntegrationReply {
    @JsonIgnore
    String getAggregateId();
}
