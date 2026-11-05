package com.algaworks.algashop.billing.infrastructure.persistence.reply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// busca por invoiceId (o id da entidade) e o acesso que o envio do reply vai usar
public interface PendingReplyRepository extends JpaRepository<PendingReplyEntity, UUID> {
}
