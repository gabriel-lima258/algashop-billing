package com.algaworks.algashop.billing.infrastructure.security.check;

import com.algaworks.algashop.billing.application.security.SecurityCheckApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Implementação de SecurityCheckApplicationService baseada em OAuth2/JWT (Spring Security).
 *
 * Funcionamento:
 * - Lê a Authentication do SecurityContextHolder (contexto de segurança da requisição atual)
 *   e espera que o principal seja um JWT (token de acesso já validado pelo resource server).
 * - getAuthenticatedUserId(): extrai o claim "sub" (subject) do JWT e o converte para UUID.
 *   Se a autenticação for de máquina, lança AccessDeniedException, pois clients não têm user ID.
 * - isAuthenticated(): delega para Authentication.isAuthenticated().
 * - isMachineAuthenticated(): detecta o fluxo client_credentials comparando "aud" e "sub" —
 *   em tokens de máquina o subject é o próprio client_id, que também aparece na audience;
 *   em tokens de usuário o subject é o UUID do usuário e não está na audience.
 *
 * Fica na camada de infraestrutura porque depende do Spring Security; a camada de
 * aplicação enxerga apenas a interface.
 */
@Service("securityCheck")
@Slf4j
public class OAuth2SecurityCheckApplicationServiceImpl implements SecurityCheckApplicationService {

    @Override
    public UUID getAuthenticatedUserId() {
        if (isMachineAuthenticated()) {
            throw new AccessDeniedException("Machina users does not have user ID");
        }
        Jwt jwt = getJwt();

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in JWT subject: {}", jwt.getSubject(), e);
            throw new AuthorizationDeniedException("Invalid user ID in JWT subject");
        }
    }

    @Override
    public boolean isAuthenticated() {
        try {
            return getAuthentication().isAuthenticated();
        } catch (IllegalStateException e) {
            log.debug(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean isMachineAuthenticated() {
        Jwt jwt;

        try {
            jwt = getJwt();
        } catch (IllegalStateException e) {
            log.debug(e.getMessage(), e);
            return false;
        }

        // aud e OPCIONAL no JWT: getAudience() devolve null quando o claim nao vem.
        // Sem o guarda, o AuditorAware (que chama este metodo antes de CADA persistencia)
        // derruba toda escrita com NPE.
        List<String> audience = jwt.getAudience();
        return audience != null && audience.contains(jwt.getSubject());
    }

    private Jwt getJwt() {
        Authentication authentication = getAuthentication();
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("Authentication principal is not a JWT");
    }

    // retorna o contexto de autenticação de usuario
    private Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authentication found");
        }
        return authentication;
    }
}
