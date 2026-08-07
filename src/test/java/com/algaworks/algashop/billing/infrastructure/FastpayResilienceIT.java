package com.algaworks.algashop.billing.infrastructure;

import com.algaworks.algashop.billing.infrastructure.creditcard.fastpay.FastpayCreditCardInput;
import com.algaworks.algashop.billing.infrastructure.creditcard.fastpay.FastpayCreditCardResponse;
import com.algaworks.algashop.billing.infrastructure.creditcard.fastpay.ResilientFastpayCreditCardAPIClient;
import com.algaworks.algashop.billing.infrastructure.payment.fastpay.FastpayPaymentInput;
import com.algaworks.algashop.billing.infrastructure.payment.fastpay.ResilientFastpayPaymentAPIClient;
import com.algaworks.algashop.billing.presentation.BadGatewayException;
import com.algaworks.algashop.billing.presentation.GatewayTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryCircuitBreaker;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryConfig;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Fixa a regra que diferencia o billing do ordering: o eixo da resiliencia e a IDEMPOTENCIA,
// nao o recurso.
//
//   capture / create de cartao  -> fastpayWriteCB, SEM retry (1 requisicao)
//   findByCode / findById       -> fastpayCB,      COM retry (4 requisicoes)
//
// Um capture retentado e uma cobranca possivelmente duplicada, entao a contagem de
// requisicoes no WireMock nao e detalhe de teste - e a garantia principal desta classe.
//
// Os tempos do circuito vem em milissegundos: com os defaults de producao, cada caso de
// retry levaria 21s.
@TestPropertySource(properties = {
        "algashop.resilience.circuit-breaker.max-retries=3",
        "algashop.resilience.circuit-breaker.delay=20ms",
        "algashop.resilience.circuit-breaker.multiplier=1",
        "algashop.resilience.circuit-breaker.open-timeout=200ms",
        "algashop.resilience.circuit-breaker.reset-timeout=5s"
})
class FastpayResilienceIT extends AbstractFastpayImplIT {

    // referenceCodes que os mappings de fastpay-failures.json reconhecem
    private static final String SERVER_ERROR_REFERENCE = "00000000-0000-0000-0000-000000000500";
    private static final String CLIENT_ERROR_REFERENCE = "00000000-0000-0000-0000-000000000400";
    private static final String CONNECTION_LOST_REFERENCE = "00000000-0000-0000-0000-000000000999";
    private static final String SERVER_ERROR_CUSTOMER = "00000000-0000-0000-0000-000000000500";

    private static final String PAYMENTS_URL = "/api/v1/payments";

    @Autowired
    private ResilientFastpayPaymentAPIClient paymentAPIClient;

    @Autowired
    private ResilientFastpayCreditCardAPIClient creditCardAPIClient;

    @Autowired
    private CircuitBreakerFactory<FrameworkRetryConfig, FrameworkRetryConfigBuilder> circuitBreakerFactory;

    @BeforeAll
    static void beforeAll() {
        startMock();
    }

    @AfterAll
    static void afterAll() {
        stopMock();
    }

    @BeforeEach
    void resetResilienceState() {
        // o journal do WireMock e o estado do circuito sao por servidor/contexto, nao por
        // teste: sem reset, um teste herda as contagens e o circuito aberto do anterior
        wireMockServer.resetRequests();
        resetCircuitBreaker("fastpayCB");
        resetCircuitBreaker("fastpayWriteCB");
    }

    @Test
    void shouldNotRetryCaptureWhenGatewayAnswersServerError() {
        assertThatThrownBy(() -> paymentAPIClient.capture(captureInput(SERVER_ERROR_REFERENCE)))
                .isInstanceOf(BadGatewayException.ServerErrorException.class);

        // A GARANTIA MAIS IMPORTANTE DA SUITE: uma unica tentativa de cobranca.
        // 5xx esta no includes da politica de leitura, mas o capture usa o fastpayWriteCB,
        // que tem maxRetries(0). Se alguem trocar o circuito deste metodo, este teste quebra.
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(PAYMENTS_URL)));
    }

    @Test
    void shouldNotRetryCaptureWhenConnectionIsLost() {
        // Timeout e queda de conexao sao o caso mais perigoso do capture: nao cancelam a
        // autorizacao do outro lado. Nunca podem ser repetidos.
        assertThatThrownBy(() -> paymentAPIClient.capture(captureInput(CONNECTION_LOST_REFERENCE)))
                .isInstanceOf(GatewayTimeoutException.class);

        wireMockServer.verify(1, postRequestedFor(urlEqualTo(PAYMENTS_URL)));
    }

    @Test
    void shouldFailWithBadGatewayWhenCaptureIsRejected() {
        assertThatThrownBy(() -> paymentAPIClient.capture(captureInput(CLIENT_ERROR_REFERENCE)))
                .isInstanceOf(BadGatewayException.ClientErrorException.class);

        wireMockServer.verify(1, postRequestedFor(urlEqualTo(PAYMENTS_URL)));
    }

    @Test
    void shouldRetryFindByCodeWhenGatewayAnswersServerError() {
        assertThatThrownBy(() -> paymentAPIClient.findByCode("pay_server_error"))
                .isInstanceOf(BadGatewayException.ServerErrorException.class);

        // leitura idempotente: 1 chamada + 3 retentativas
        wireMockServer.verify(4, getRequestedFor(urlEqualTo("/api/v1/payments/pay_server_error")));
    }

    @Test
    void shouldOpenCircuitAndStopCallingGatewayAfterCaptureFailure() {
        assertThatThrownBy(() -> paymentAPIClient.capture(captureInput(SERVER_ERROR_REFERENCE)))
                .isInstanceOf(BadGatewayException.class);

        wireMockServer.verify(1, postRequestedFor(urlEqualTo(PAYMENTS_URL)));

        // circuito aberto: mesma excecao, mas sem sair para a rede
        assertThatThrownBy(() -> paymentAPIClient.capture(captureInput(SERVER_ERROR_REFERENCE)))
                .isInstanceOf(BadGatewayException.class);

        wireMockServer.verify(1, postRequestedFor(urlEqualTo(PAYMENTS_URL)));
    }

    @Test
    void shouldNotRetryCardCreationWhenGatewayAnswersServerError() {
        FastpayCreditCardInput input = FastpayCreditCardInput.builder()
                .tokenizedCard("tok_whatever")
                .customerCode(SERVER_ERROR_CUSTOMER)
                .build();

        assertThatThrownBy(() -> creditCardAPIClient.create(input))
                .isInstanceOf(BadGatewayException.ServerErrorException.class);

        // create de cartao tambem nao e idempotente: retentar criaria tokens duplicados
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/api/v1/credit-cards")));
    }

    @Test
    void shouldReturnEmptyWhenCardDoesNotExistInGateway() {
        // 404 aqui e caso de negocio legitimo - o cartao pode ter sido removido no gateway
        Optional<FastpayCreditCardResponse> card = creditCardAPIClient.findById("cc_not_found");

        assertThat(card).isEmpty();
    }

    private FastpayPaymentInput captureInput(String referenceCode) {
        return FastpayPaymentInput.builder()
                .referenceCode(referenceCode)
                .totalAmount(new BigDecimal("100.00"))
                .method("GATEWAY_BALANCE")
                .fullName("John Doe")
                .document("12345")
                .phone("5511912341234")
                .addressLine1("Bourbon Street, 2000")
                .addressLine2("apt 122")
                .zipCode("12321")
                .replyToUrl("http://localhost/webhook")
                .build();
    }

    private void resetCircuitBreaker(String id) {
        FrameworkRetryCircuitBreaker circuitBreaker =
                (FrameworkRetryCircuitBreaker) circuitBreakerFactory.create(id);

        circuitBreaker.getCircuitBreakerPolicy().reset();
    }
}
