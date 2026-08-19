package com.algaworks.algashop.billing.infrastructure.security.token;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Liga o converter customizado ao resource server.
 *
 * O .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults())) da SecurityConfig NAO recebe
 * o converter como parametro: ele PROCURA no contexto um bean do tipo
 * JwtAuthenticationConverter e usa se encontrar. E isso que este arquivo faz existir.
 *
 * A consequencia de esquece-lo e a pior possivel: sem este bean, o converter delegante fica
 * inerte - o contexto sobe, os testes passam, o token continua sendo aceito, e o claim
 * "role" simplesmente nunca vira authority. Toda regra baseada em papel passaria a negar
 * todo mundo, e o unico sintoma seria um 403 sem explicacao.
 *
 * E a mesma familia do @Configuration perdido num rename (Fase 24) e do @TestConfiguration
 * que ninguem importava (Fase 26): a ligacao entre as pecas nao e verificada em compilacao,
 * so por comportamento.
 */
@Configuration
public class JwtAuthenticationConverterConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(JwtGrantedAuthoritiesDelegatingConverter authoritiesDelegatingConverter) {
        var jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesDelegatingConverter);
        return jwtAuthenticationConverter;
    }
}
