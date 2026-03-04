package com.algaworks.algashop.billing.domain.model;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Classe base abstrata que fornece campos de auditoria automática para todas as entidades do domínio.
 *
 * Qualquer entidade que estender esta classe terá automaticamente:
 * - Rastreamento de QUEM criou e QUANDO criou
 * - Rastreamento de QUEM modificou por último e QUANDO modificou
 * - Controle de versão para proteção contra atualizações concorrentes (optimistic locking)
 *
 * Exemplo: Invoice, CreditCard e qualquer outra entidade JPA que precise de auditoria
 * herdam desta classe e ganham esses campos "de graça", sem precisar repetir código.
 *
 * Para funcionar, o projeto precisa ter @EnableJpaAuditing na configuração do Spring
 * e um AuditorAware<UUID> que forneça o ID do usuário atual (ex: do contexto de segurança).
 */
@Getter
@Setter(AccessLevel.PRIVATE) // apenas hibernate pode chamar
@MappedSuperclass // indica que esta classe NÃO é uma entidade por si só
@EntityListeners(AuditingEntityListener.class) // preenche a entidade herdada automaticamente antes de persistir
public abstract class AbstractAuditableAggregateRoot<T extends AbstractAggregateRoot<T>>
        extends AbstractAggregateRoot<T> {

    // @CreatedBy: preenchido automaticamente com o ID do usuário que criou o registro.
    @CreatedBy
    protected UUID createdByUserId;

    // @CreatedDate: preenchido automaticamente com a data/hora exata do INSERT.
    @CreatedDate
    protected OffsetDateTime createdAt;

    // @LastModifiedBy: preenchido automaticamente com o ID do usuário que fez a última modificação.
    @LastModifiedBy
    protected UUID lastModifiedByUserId;

    // @LastModifiedDate: preenchido automaticamente com a data/hora da última modificação.
    @LastModifiedDate
    protected OffsetDateTime lastModifiedDate;

    // @Version (Spring Data / JPA): implementa o padrão Optimistic Locking.
    //
    // Como funciona:
    // 1. Quando a entidade é criada, version = 0
    // 2. A cada UPDATE, o JPA incrementa automaticamente: version = version + 1
    // 3. No UPDATE, o JPA gera: UPDATE invoice SET ... WHERE id = ? AND version = ?
    // 4. Se dois usuários tentarem atualizar a mesma fatura ao mesmo tempo:
    //    - O primeiro consegue (version bate)
    //    - O segundo FALHA com OptimisticLockException (version já mudou)
    //
    // Isso evita o problema de "lost update" (uma atualização sobrescrevendo a outra)
    // sem precisar de locks pesados no banco de dados (pessimistic locking).
    // É a estratégia ideal para aplicações web onde conflitos são raros.
    @Version
    protected long version;

}
