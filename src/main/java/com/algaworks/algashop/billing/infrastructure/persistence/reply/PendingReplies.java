package com.algaworks.algashop.billing.infrastructure.persistence.reply;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Guarda "para quem e com qual correlacao responder" enquanto o resultado da fatura nao
// existe. Sao os padroes Correlation Identifier + Return Address do EIP, persistidos:
// o comando trouxe os dois nos headers, e eles precisam sobreviver ate o pagamento se
// resolver - o que pode levar segundos (captura) ou minutos (webhook).
// Mora na infraestrutura porque correlacao e topico sao metadados de transporte, nao
// regra de negocio da fatura.
@Component
@RequiredArgsConstructor
public class PendingReplies {

    private final PendingReplyRepository repository;

    @Transactional
    public void register(UUID invoiceId, String orderId, String correlationId, String replyTopic) {
        repository.save(new PendingReplyEntity(invoiceId, orderId, correlationId, replyTopic));
    }
}
