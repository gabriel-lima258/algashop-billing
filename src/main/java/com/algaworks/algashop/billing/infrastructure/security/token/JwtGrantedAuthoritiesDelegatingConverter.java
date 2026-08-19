package com.algaworks.algashop.billing.infrastructure.security.token;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;

/**
 * Traduz o JWT em authorities do Spring Security - somando o que o padrao ja faz com o que
 * o AlgaShop precisa.
 *
 * O JwtGrantedAuthoritiesConverter padrao le o claim "scope" e prefixa cada valor com
 * "SCOPE_". E so isso que ele faz, e e por isso que as anotacoes do projeto escrevem
 * hasAuthority('SCOPE_users:read') em vez de hasScope('users:read').
 *
 * Esta classe DELEGA para ele (nao o substitui) e acrescenta uma authority a mais, vinda do
 * claim "role" que o JwtTokenCustomizer escreveu:
 *
 *     scope: "users:read users:write"  ->  SCOPE_users:read, SCOPE_users:write
 *     role:  "MANAGER"                 ->  ROLE_MANAGER
 *
 * POR QUE O PREFIXO "ROLE_" IMPORTA
 * Nao e enfeite: hasRole('MANAGER') do Spring monta a string "ROLE_MANAGER" por conta
 * propria e compara. Gravar a authority como "MANAGER" faria hasRole('MANAGER') falhar em
 * silencio - e hasAuthority('MANAGER') passaria. Duas formas de escrever a mesma intencao
 * que NAO sao equivalentes, e nenhuma delas e verificada em compilacao.
 *
 * DELEGAR EM VEZ DE REESCREVER e a decisao que mantem o comportamento padrao intacto: se o
 * Spring mudar como interpreta "scope" (ou passar a ler "scp" tambem), este codigo herda a
 * mudanca sem alteracao.
 *
 * As quatro copias deste arquivo - uma por servico - sao identicas fora do package, pela
 * mesma razao do SecurityCheckApplicationService: microsservico independente nao compartilha
 * jar de aplicacao. O custo e conhecido: um ajuste aqui e quatro edicoes.
 */
@Component
public class JwtGrantedAuthoritiesDelegatingConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopeGrantedAuthorities =
            new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        HashSet<GrantedAuthority> grantedAuthorities =
                new HashSet<>(scopeGrantedAuthorities.convert(jwt));

        String role = jwt.getClaimAsString("role");
        if (StringUtils.isNotBlank(role)) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }

        return grantedAuthorities;
    }
}
