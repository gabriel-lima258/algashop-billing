package com.algaworks.algashop.billing.infrastructure.resilience;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.cloud.circuitbreaker.retry.CircuitBreakerRetryPolicy;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.algaworks.algashop.billing.infrastructure.resilience.SpringCircuitBreakerConfig.fastpayReadCB;
import static com.algaworks.algashop.billing.infrastructure.resilience.SpringCircuitBreakerConfig.fastpayWriteCB;

// Expõe o estado dos circuit breakers no /actuator/health, fechando a pendencia da
// Fase 16: ate aqui o unico jeito de saber que um circuito abriu era ler log.info.
//
// O QUE ELE REPORTA, e o que NAO reporta:
//   CLOSED / HALF_OPEN  -> UP        o circuito nao esta cortando chamadas
//   OPEN                -> DEGRADED  a integracao caiu; o resto do servico funciona
// Nunca DOWN, de proposito. Gateway fora do ar significa que a cobranca falha, nao que
// esta instancia deva sair de rotacao - ela continua servindo consulta de fatura e de
// cartao. Por isso "circuitbreakers" tambem NAO esta no group readiness.
//
// Os DOIS circuitos aqui sao do MESMO host (Fastpay), separados por idempotencia: um para
// leitura, com retry, outro para escrita, sem. Ver SpringCircuitBreakerConfig.
//
// O DETALHE QUE FAZ TUDO FUNCIONAR - ou nao: o construtor pede os circuitos a factory
// pelo MESMO id que os clients usam (dai as constantes em SpringCircuitBreakerConfig).
// Isso so vale se create(id) devolver a instancia ja configurada, e nao uma nova a cada
// chamada: o estado vive dentro do CircuitBreakerRetryPolicy, entao uma instancia nova
// reportaria CLOSED para sempre - com o endpoint respondendo bonito e mentindo.
//
// Os ids sao HARDCODED aqui. Um circuito novo no SpringCircuitBreakerConfig nao aparece
// neste indicador sem alguem editar esta classe - o acoplamento e proposital enquanto sao
// dois, e vira problema quando forem muitos.
@Component("circuitbreakers")
public class CustomFrameworkRetryCircuitBreakerHealthIndicator implements HealthIndicator {

    private final List<FrameworkRetryCircuitBreaker> circuitBreakers = new ArrayList<>();

    public CustomFrameworkRetryCircuitBreakerHealthIndicator(CircuitBreakerFactory circuitBreakerFactory) {
        circuitBreakers.add((FrameworkRetryCircuitBreaker) circuitBreakerFactory.create(fastpayReadCB));
        circuitBreakers.add((FrameworkRetryCircuitBreaker) circuitBreakerFactory.create(fastpayWriteCB));
    }

    @Override
    public @Nullable Health health() {
        Map<String, Object> indicatorDetails = new HashMap<>();
        String indicatorStatus = "UP";
        Throwable lastException = null;

        for (FrameworkRetryCircuitBreaker circuitBreaker : circuitBreakers) {
            var policy = circuitBreaker.getConfig().getCircuitBreakerRetryPolicy();
            var state = policy.getState();
            Map<String, Object> cbDetails = new HashMap<>();
            cbDetails.put("state", state.name());

            if (state == CircuitBreakerRetryPolicy.State.OPEN) {
                indicatorStatus = "DEGRADED";
                if (policy.getLastException() != null &&
                    policy.getLastException().getCause() != null) {
                    lastException = policy.getLastException().getCause();
                    cbDetails.put("lastException", lastException.getMessage());
                } else {
                    cbDetails.put("lastException", null);
                }
            }

            indicatorDetails.put(circuitBreaker.getId(), cbDetails);
        }

        Health.Builder builder = Health.status(indicatorStatus).withDetails(indicatorDetails);

        if (indicatorStatus.equals("DEGRADED") && lastException != null) {
            builder.withException(lastException);
        }

        return builder.build();
    }
}
