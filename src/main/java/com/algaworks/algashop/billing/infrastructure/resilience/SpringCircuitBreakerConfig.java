package com.algaworks.algashop.billing.infrastructure.resilience;

import com.algaworks.algashop.billing.presentation.BadGatewayException;
import com.algaworks.algashop.billing.presentation.GatewayTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

import java.time.Duration;

// DOIS circuitos para o MESMO host, e isso e deliberado.
//
// Nesta lib o retry e o circuito vem acoplados no mesmo run(): a RetryPolicy faz parte da
// config do breaker. Como no billing metade das operacoes COBRA DINHEIRO, precisamos de
// "circuito sim, retry nao" - e a unica forma de expressar isso e uma segunda config.
//
//   fastpayCB       -> leituras idempotentes (findByCode, findById de cartao). COM retry.
//   fastpayWriteCB  -> capture, register e delete de cartao. SEM retry (maxRetries = 0).
//
// Por que nao um circuito so, traduzindo as excecoes do capture para tipos fora do
// includes: funcionaria, mas o "nao retenta" passaria a depender de um detalhe silencioso
// da traducao. Qualquer mudanca futura la reativaria o retry na cobranca sem ninguem
// perceber. Em codigo que cobra dinheiro, explicito ganha de esperto.
//
// O custo: dois estados independentes para a mesma dependencia. Se o Fastpay cair, os dois
// abrem separadamente (cada um na sua primeira falha).
@Configuration
public class SpringCircuitBreakerConfig {

    public static final String fastpayReadCB = "fastpayCB";
    public static final String fastpayWriteCB = "fastpayWriteCB";

    // Customizer<...Factory> e o gancho que o Spring Cloud chama uma unica vez, no
    // startup, entregando a fabrica de circuit breakers para configurarmos.
    @Bean
    public Customizer<FrameworkRetryCircuitBreakerFactory> defaultCustomizer(
            @Value("${algashop.resilience.circuit-breaker.max-retries:3}") long maxRetries,
            @Value("${algashop.resilience.circuit-breaker.delay:3s}") Duration delay,
            @Value("${algashop.resilience.circuit-breaker.multiplier:2}") double multiplier,
            @Value("${algashop.resilience.circuit-breaker.open-timeout:5s}") Duration openTimeout,
            @Value("${algashop.resilience.circuit-breaker.reset-timeout:30s}") Duration resetTimeout) {

        // ServerErrorException e ClientErrorException agora herdam de BadGatewayException,
        // mas o includes continua listando SO a ServerErrorException: o retry casa por
        // assignability, entao 4xx e a BadGatewayException generica seguem sem retry.
        RetryPolicy readRetryPolicy = RetryPolicy.builder()
                .maxRetries(maxRetries)
                .multiplier(multiplier)
                .delay(delay)
                .includes(GatewayTimeoutException.class, BadGatewayException.ServerErrorException.class)
                .build();

        // maxRetries(0) = uma tentativa e acabou. O includes fica sem efeito pratico, mas
        // continua ali para o dia em que existir chave de idempotencia acordada com o
        // gateway e der para retentar com seguranca.
        RetryPolicy writeRetryPolicy = RetryPolicy.builder()
                .maxRetries(0)
                .includes(GatewayTimeoutException.class, BadGatewayException.ServerErrorException.class)
                .build();

        return factory -> {
            factory.configure(builder -> builder
                    .retryPolicy(readRetryPolicy)
                    .openTimeout(openTimeout)
                    .resetTimeout(resetTimeout)
                    .build(), fastpayReadCB);

            factory.configure(builder -> builder
                    .retryPolicy(writeRetryPolicy)
                    .openTimeout(openTimeout)
                    .resetTimeout(resetTimeout)
                    .build(), fastpayWriteCB);
        };
    }
}
