package com.algaworks.algashop.billing.application.security;

import java.util.UUID;

/**
 * Porta (interface) da camada de aplicação para consultas de segurança sobre o usuário autenticado.
 *
 * Abstrai a tecnologia de segurança (OAuth2/JWT) para que a camada de aplicação
 * não dependa do Spring Security — a implementação concreta fica na infraestrutura
 * (OAuth2SecurityCheckApplicationServiceImpl).
 *
 * - getAuthenticatedUserId(): retorna o UUID do usuário autenticado (extraído do token)
 * - isAuthenticated(): indica se há uma autenticação ativa no contexto
 * - isMachineAuthenticated(): indica se a autenticação é de máquina (client_credentials)
 */
public interface SecurityCheckApplicationService {
    UUID getAuthenticatedUserId();
    boolean isAuthenticated();
    boolean isMachineAuthenticated();
}
