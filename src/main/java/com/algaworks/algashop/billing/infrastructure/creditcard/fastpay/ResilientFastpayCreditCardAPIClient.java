package com.algaworks.algashop.billing.infrastructure.creditcard.fastpay;

import com.algaworks.algashop.billing.presentation.BadGatewayException;
import com.algaworks.algashop.billing.presentation.GatewayTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryCircuitBreaker;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryConfig;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.core.retry.RetryException;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.function.Supplier;

// Espelha o ResilientFastpayPaymentAPIClient: mesmo Fastpay, mesma divisao por idempotencia.
//
//   create / delete -> writeCircuitBreaker (SEM retry)
//   findById        -> readCircuitBreaker  (com retry)
//
// O create nao e idempotente: retentar cria cartoes duplicados no gateway. Menos grave que
// cobrar duas vezes, mas suja o cadastro e ninguem descobre depois qual token e o bom.
//
// DIFERENCA em relacao ao delete: ele e idempotente no gateway (apagar o que ja foi apagado
// e no-op), mas fica no circuito de escrita mesmo assim - o CreditCardManagementService
// apaga do nosso banco ANTES de chamar o gateway, entao uma retentativa aqui aconteceria com
// o registro local ja removido. Nao ha ganho em repetir.
@Component
@Slf4j
public class ResilientFastpayCreditCardAPIClient {

    private final FastpayCreditCardAPIClient fastpayCreditCardAPIClient;
    private final FrameworkRetryCircuitBreaker readCircuitBreaker;
    private final FrameworkRetryCircuitBreaker writeCircuitBreaker;

    public ResilientFastpayCreditCardAPIClient(FastpayCreditCardAPIClient fastpayCreditCardAPIClient,
                                               CircuitBreakerFactory<FrameworkRetryConfig, FrameworkRetryConfigBuilder> circuitBreakerFactory) {
        this.fastpayCreditCardAPIClient = fastpayCreditCardAPIClient;
        this.readCircuitBreaker = (FrameworkRetryCircuitBreaker) circuitBreakerFactory.create("fastpayCB");
        this.writeCircuitBreaker = (FrameworkRetryCircuitBreaker) circuitBreakerFactory.create("fastpayWriteCB");
    }

    @ConcurrencyLimit(10) // bulkhead: no maximo 10 threads aqui dentro; as demais BLOQUEIAM
    public FastpayCreditCardResponse create(FastpayCreditCardInput input) {
        return execute(writeCircuitBreaker, "create card", () -> doCreate(input));
    }

    // Optional porque 404 aqui e caso de NEGOCIO legitimo: o cartao pode ter sido removido
    // direto no gateway. Diferente do capture, onde nenhum 4xx e esperado.
    @ConcurrencyLimit(10)
    public Optional<FastpayCreditCardResponse> findById(String gatewayCode) {
        return execute(readCircuitBreaker, "findById card " + gatewayCode,
                () -> doFindById(gatewayCode));
    }

    @ConcurrencyLimit(10)
    public void delete(String gatewayCode) {
        execute(writeCircuitBreaker, "delete card " + gatewayCode, () -> {
            doDelete(gatewayCode);
            return null;
        });
    }

    private <T> T execute(FrameworkRetryCircuitBreaker circuitBreaker, String operation, Supplier<T> call) {
        log.info("Fastpay {} | circuit {}", operation, circuitBreaker.getCircuitBreakerPolicy().getState());

        try {
            return circuitBreaker.run(call::get);
        } catch (NoFallbackAvailableException e) {
            throw unwrapException(e);
        }
    }

    private FastpayCreditCardResponse doCreate(FastpayCreditCardInput input) {
        try {
            FastpayCreditCardResponse response = fastpayCreditCardAPIClient.create(input);

            // 201 sem corpo: resposta invalida do upstream. Devolver null empurraria o NPE
            // para o mapeador, fora do circuito, onde a stack aponta para o lugar errado.
            if (response == null) {
                throw new BadGatewayException("Fastpay API returned an empty body on card creation");
            }

            return response;
        } catch (RestClientException e) {
            log.warn("Fastpay card creation failed | {}", describe(e));
            throw translateException(e);
        }
    }

    private Optional<FastpayCreditCardResponse> doFindById(String gatewayCode) {
        try {
            return Optional.ofNullable(fastpayCreditCardAPIClient.findById(gatewayCode));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Fastpay card lookup failed for {} | {}", gatewayCode, describe(e));
            throw translateException(e);
        }
    }

    private void doDelete(String gatewayCode) {
        try {
            fastpayCreditCardAPIClient.delete(gatewayCode);
        } catch (RestClientException e) {
            log.warn("Fastpay card deletion failed for {} | {}", gatewayCode, describe(e));
            throw translateException(e);
        }
    }

    private RuntimeException unwrapException(NoFallbackAvailableException e) {
        Throwable cause = (e.getCause() instanceof RetryException re) ? re.getCause() : e.getCause();

        return switch (cause) {
            case GatewayTimeoutException gte -> gte;
            case BadGatewayException bge -> bge;
            case null, default -> e;
        };
    }

    private RuntimeException translateException(RestClientException e) {
        if (e.getCause() instanceof SocketTimeoutException || e instanceof ResourceAccessException) {
            return new GatewayTimeoutException("Fastpay API Timeout", e);
        }

        // 4xx que nao e 404 (esse virou Optional.empty antes de chegar aqui)
        if (e instanceof HttpClientErrorException) {
            return new BadGatewayException.ClientErrorException("Fastpay API Bad Gateway", e);
        }

        if (e instanceof HttpServerErrorException) {
            return new BadGatewayException.ServerErrorException("Fastpay API Bad Gateway", e);
        }

        return new BadGatewayException("Fastpay API Bad Gateway", e);
    }

    private String describe(RestClientException e) {
        return e instanceof HttpStatusCodeException statusError
                ? "HTTP " + statusError.getStatusCode()
                : e.getClass().getSimpleName();
    }

}
