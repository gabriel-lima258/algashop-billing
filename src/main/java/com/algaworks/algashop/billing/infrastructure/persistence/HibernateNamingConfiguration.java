package com.algaworks.algashop.billing.infrastructure.persistence;

import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyComponentPathImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Define a estratégia de nomeação implícita de colunas para @Embedded e @Embeddable.
// Usa o caminho completo do componente como prefixo, evitando conflitos de nome.
// Ex: Invoice > Payer > Address > number => coluna: payer_address_number
@Configuration
public class HibernateNamingConfiguration {

    @Bean
    public ImplicitNamingStrategy implicit() {
        return new ImplicitNamingStrategyComponentPathImpl();
    }
}
