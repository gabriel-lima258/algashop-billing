package com.algaworks.algashop.billing.infrastructure.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

// Habilita o mecanismo de auditoria do Spring Data JPA.
// Com essa configuração, campos anotados com @CreatedDate, @LastModifiedDate,
// @CreatedBy e @LastModifiedBy são preenchidos automaticamente pelo framework
// sempre que uma entidade é persistida ou atualizada.
//
// - dateTimeProviderRef: define o bean responsável por fornecer a data/hora atual
//   para os campos @CreatedDate e @LastModifiedDate.
// - auditorAwareRef: define o bean responsável por fornecer o identificador do
//   usuário (auditor) para os campos @CreatedBy e @LastModifiedBy.
@Configuration
@EnableJpaAuditing(
        dateTimeProviderRef = "auditingDateTimeProvider",
        auditorAwareRef = "auditorProvider"
)
public class SpringDataAuditingConfig {

    // Fornece o instante atual (OffsetDateTime) truncado em milissegundos.
    // O truncamento evita diferenças de precisão entre o Java (nanossegundos)
    // e o banco de dados (geralmente milissegundos), garantindo que comparações
    // em testes e queries funcionem de forma consistente.
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    }

    // Fornece o identificador do usuário responsável pela operação.
    // Atualmente retorna um UUID aleatório como placeholder.
    // Quando a camada de segurança (Spring Security) estiver configurada,
    // este bean deve ser alterado para extrair o ID do usuário autenticado
    // a partir do SecurityContext (ex: SecurityContextHolder.getContext()
    // .getAuthentication().getPrincipal()).
    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> Optional.of(UUID.randomUUID());
    }
}
