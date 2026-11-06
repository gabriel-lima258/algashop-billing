package com.algaworks.algashop.billing.infrastructure.persistence.reply;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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

    // find e delete sao chamados no AFTER_COMMIT da fatura: a transacao dela ja terminou,
    // e participar dela nao gravaria nada. Por isso REQUIRES_NEW: transacao propria
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<PendingReplyEntity> find(UUID invoiceId) {
        return repository.findById(invoiceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(UUID invoiceId) {
        repository.deleteById(invoiceId);
    }
}
