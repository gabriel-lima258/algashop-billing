package com.algaworks.algashop.billing.infrastructure.payment.fastpay;

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
import java.util.function.Supplier;

import static com.algaworks.algashop.billing.infrastructure.resilience.SpringCircuitBreakerConfig.fastpayReadCB;
import static com.algaworks.algashop.billing.infrastructure.resilience.SpringCircuitBreakerConfig.fastpayWriteCB;

// Unica camada entre o dominio e o HTTP do Fastpay: resiliencia + traducao de erro.
// NAO ha cache aqui, nem deve haver - e dado financeiro.
//
// DOIS CIRCUITOS, e a escolha entre eles e a decisao mais importante desta classe:
//
//   capture          -> writeCircuitBreaker (SEM retry)   POST que cobra dinheiro
//   findByCode       -> readCircuitBreaker  (com retry)   GET idempotente
//   refund / cancel  -> readCircuitBreaker  (com retry)   idempotentes no gateway
//
// O capture nao pode ser retentado: timeout num POST de cobranca NAO cancela a autorizacao
// do outro lado - significa "nao sei se cobrou". Repetir e o caminho classico da cobranca
// dupla. Quem resolve a incerteza e a conciliacao (webhook replyToUrl + findByCode), nao
// outra tentativa.
@Component
@Slf4j
public class ResilientFastpayPaymentAPIClient {

    private final FastpayPaymentAPIClient fastpayPaymentAPIClient;
    private final FrameworkRetryCircuitBreaker readCircuitBreaker;
    private final FrameworkRetryCircuitBreaker writeCircuitBreaker;

    public ResilientFastpayPaymentAPIClient(FastpayPaymentAPIClient fastpayPaymentAPIClient,
                                            CircuitBreakerFactory<FrameworkRetryConfig, FrameworkRetryConfigBuilder> circuitBreakerFactory) {
        this.fastpayPaymentAPIClient = fastpayPaymentAPIClient;
        this.readCircuitBreaker = (FrameworkRetryCircuitBreaker) circuitBreakerFactory.create(fastpayReadCB);
        this.writeCircuitBreaker = (FrameworkRetryCircuitBreaker) circuitBreakerFactory.create(fastpayWriteCB);
    }

    @ConcurrencyLimit(10) // bulkhead: no maximo 10 threads aqui dentro; as demais BLOQUEIAM
    public FastpayPaymentModel capture(FastpayPaymentInput request) {
        return execute(writeCircuitBreaker, "capture " + request.getReferenceCode(),
                () -> doCapture(request));
    }

    @ConcurrencyLimit(10)
    public FastpayPaymentModel findByCode(String gatewayCode) {
        return execute(readCircuitBreaker, "findById " + gatewayCode,
                () -> doFindPayment(gatewayCode));
    }

    @ConcurrencyLimit(10)
    public void refund(String gatewayCode) {
        execute(readCircuitBreaker, "refund " + gatewayCode, () -> {
            doRefund(gatewayCode);
            return null;
        });
    }

    @ConcurrencyLimit(10)
    public void cancel(String gatewayCode) {
        execute(readCircuitBreaker, "cancel " + gatewayCode, () -> {
            doCancel(gatewayCode);
            return null;
        });
    }

    // Ponto unico de entrada no circuito - antes esse try/catch estava copiado em cada
    // metodo publico. O log de estado sai ANTES da chamada: numa falha ele mostra o estado
    // anterior, e a transicao so aparece na requisicao seguinte.
    private <T> T execute(FrameworkRetryCircuitBreaker circuitBreaker, String operation, Supplier<T> call) {
        log.info("Fastpay {} | circuit {}", operation, circuitBreaker.getCircuitBreakerPolicy().getState());

        try {
            return circuitBreaker.run(call::get);
        } catch (NoFallbackAvailableException e) {
            throw unwrapException(e);
        }
    }

    private FastpayPaymentModel doCapture(FastpayPaymentInput request) {
        try {
            return fastpayPaymentAPIClient.capture(request);
        } catch (RestClientException e) {
            log.warn("Fastpay capture failed for reference {} | {}", request.getReferenceCode(), describe(e));
            throw translateException(e);
        }
    }

    private FastpayPaymentModel doFindPayment(String paymentId) {
        try {
            return fastpayPaymentAPIClient.findById(paymentId);
        } catch (RestClientException e) {
            log.warn("Fastpay findById failed for payment {} | {}", paymentId, describe(e));
            throw translateException(e);
        }
    }

    private void doRefund(String paymentId) {
        try {
            fastpayPaymentAPIClient.refund(paymentId);
        } catch (RestClientException e) {
            log.warn("Fastpay refund failed for payment {} | {}", paymentId, describe(e));
            throw translateException(e);
        }
    }

    private void doCancel(String paymentId) {
        try {
            fastpayPaymentAPIClient.cancel(paymentId);
        } catch (RestClientException e) {
            log.warn("Fastpay cancel failed for payment {} | {}", paymentId, describe(e));
            throw translateException(e);
        }
    }

    // A falha chega em camadas: NoFallbackAvailableException -> RetryException -> excecao
    // traduzida (o RetryTemplate embrulha ate o que nem era retentavel). Sem desempacotar,
    // o ApiExceptionHandler cai no handler generico e devolve 500 em vez de 502/504.
    private RuntimeException unwrapException(NoFallbackAvailableException e) {
        Throwable cause = (e.getCause() instanceof RetryException re) ? re.getCause() : e.getCause();

        return switch (cause) {
            case GatewayTimeoutException gte -> gte;
            case BadGatewayException bge -> bge; // pega tambem Server/ClientErrorException
            case null, default -> e;
        };
    }

    // traduz o vocabulario do RestClient para o da API (504 / 502)
    private RuntimeException translateException(RestClientException e) {
        // connect/read timeout ou rede fora
        if (e.getCause() instanceof SocketTimeoutException || e instanceof ResourceAccessException) {
            return new GatewayTimeoutException("Fastpay API Timeout", e);
        }

        // 4xx - erro determinístico, repetir daria o mesmo
        if (e instanceof HttpClientErrorException) {
            return new BadGatewayException.ClientErrorException("Fastpay API Bad Gateway", e);
        }

        // 5xx - o unico tipo daqui que esta no includes da RetryPolicy de leitura
        if (e instanceof HttpServerErrorException) {
            return new BadGatewayException.ServerErrorException("Fastpay API Bad Gateway", e);
        }

        // resto: corpo ilegivel, erro de conversao...
        return new BadGatewayException("Fastpay API Bad Gateway", e);
    }

    private String describe(RestClientException e) {
        return e instanceof HttpStatusCodeException statusError
                ? "HTTP " + statusError.getStatusCode()
                : e.getClass().getSimpleName();
    }

}
