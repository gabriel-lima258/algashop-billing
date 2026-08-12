package com.algaworks.algashop.billing.infrastructure.security;


import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Meta-anotacoes de escopo. Cada uma embrulha um @PreAuthorize para que o controller
// declare a INTENCAO ("quem le pedido") em vez da expressao ("hasAuthority('SCOPE_...')").
//
// Tres razoes para isso nao ser so acucar sintatico:
//
// 1. A expressao do @PreAuthorize e uma STRING avaliada em runtime. Um typo -
//    'SCOPE_orders' sem o sufixo, ou 'SCOPE_orders:raed' - compila, sobe, e NEGA
//    todo mundo em silencio. Concentrar as strings aqui reduz a superficie de erro
//    de N controllers para um arquivo, e e o que a matriz de teste consegue cobrir.
// 2. Renomear um escopo vira uma edicao, nao uma varredura.
// 3. Quem le o controller ve a regra de negocio, nao a sintaxe do Spring Security.
//
// O prefixo SCOPE_ nao e escolha nossa: o JwtGrantedAuthoritiesConverter, padrao do
// resource server, le o claim "scope" do token e prefixa cada valor com "SCOPE_" ao
// transformar em GrantedAuthority. Por isso hasAuthority('SCOPE_x') e nao hasScope('x').
public class SecurityAnnotations {

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_invoices:read')")
    public @interface CanReadInvoices {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_invoices:write')")
    public @interface CanWriteInvoices {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_credit-cards:read')")
    public @interface CanReadCreditCards {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_credit-cards:write')")
    public @interface CanWriteCreditCards {}

}
