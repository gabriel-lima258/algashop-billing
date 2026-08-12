package com.algaworks.algashop.billing.infrastructure.security;

import com.algaworks.algashop.billing.application.creditcard.management.CreditCardManagementService;
import com.algaworks.algashop.billing.application.creditcard.query.CreditCardQueryService;
import com.algaworks.algashop.billing.application.invoice.management.InvoiceManagementApplicationService;
import com.algaworks.algashop.billing.application.invoice.query.InvoiceQueryService;
import com.algaworks.algashop.billing.infrastructure.payment.fastpay.webhook.FastpayWebhookController;
import com.algaworks.algashop.billing.infrastructure.payment.fastpay.webhook.FastpayWebhookHandler;
import com.algaworks.algashop.billing.presentation.CreditCardController;
import com.algaworks.algashop.billing.presentation.InvoiceController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * A matriz de autorizacao do billing: para CADA rota anotada, tres perguntas.
 *
 *   sem token       -> 401 (nao autenticado)
 *   escopo errado   -> 403 (autenticado, sem permissao)
 *   escopo correto  -> passa pela seguranca
 *
 * Ver o javadoc do AuthorizationMatrixTest do ordering para as decisoes de desenho
 * comuns aos tres servicos.
 *
 * Especifico do billing: ele tem a unica rota deliberadamente ABERTA do sistema - o
 * webhook do FastPay. O teste no fim da classe fixa esse permitAll de proposito: se
 * alguem "consertar" a excecao achando que e descuido, o gateway para de conseguir
 * confirmar pagamento e nenhuma fatura fecha.
 */
@WebMvcTest(controllers = {
        InvoiceController.class,
        CreditCardController.class,
        FastpayWebhookController.class
})
@Import(BillingSecurityConfig.class)
class AuthorizationMatrixTest {

    private static final String UNRELATED_SCOPE = "SCOPE_totally:unrelated";
    private static final String JSON = "application/json";

    private static final String ORDER_ID = "0R8PSRVRB4WQH";
    private static final String CUSTOMER_ID = "41cdc65c-6158-48b0-a8e6-34c0ff8fd74e";
    private static final String CREDIT_CARD_ID = "7f8e9d0c-0000-0000-0000-000000000000";

    private static final String ADDRESS = """
            {"street":"Elm Street","number":"456","complement":"","neighborhood":"Central Park",
             "city":"Springfield","state":"Illinois","zipCode":"62704"}""";

    private static final String INVOICE_BODY = """
            {"orderId":"%s","customerId":"%s","paymentSettings":{"method":"GATEWAY_BALANCE"},
             "payer":{"fullName":"John Doe","document":"255-08-0578","email":"john@email.com",
                      "phone":"478-256-2604","address":%s},
             "items":[{"name":"Item","amount":10.00}]}"""
            .formatted(ORDER_ID, CUSTOMER_ID, ADDRESS);

    private static final String CREDIT_CARD_BODY = """
            {"customerId":"%s","tokenizedCard":"tok_abc123"}""".formatted(CUSTOMER_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean private InvoiceManagementApplicationService invoiceManagementApplicationService;
    @MockitoBean private InvoiceQueryService invoiceQueryService;
    @MockitoBean private CreditCardManagementService creditCardManagementService;
    @MockitoBean private CreditCardQueryService creditCardQueryService;
    @MockitoBean private FastpayWebhookHandler fastpayWebhookHandler;

    static Stream<Arguments> routes() {
        return Stream.of(
                // FATURAS
                Arguments.of(HttpMethod.POST, "/api/v1/orders/" + ORDER_ID + "/invoice", "SCOPE_invoices:write", JSON, INVOICE_BODY),
                Arguments.of(HttpMethod.GET, "/api/v1/orders/" + ORDER_ID + "/invoice", "SCOPE_invoices:read", null, null),

                // CARTOES
                Arguments.of(HttpMethod.POST, "/api/v1/customers/" + CUSTOMER_ID + "/credit-cards", "SCOPE_credit-cards:write", JSON, CREDIT_CARD_BODY),
                Arguments.of(HttpMethod.GET, "/api/v1/customers/" + CUSTOMER_ID + "/credit-cards", "SCOPE_credit-cards:read", null, null),
                Arguments.of(HttpMethod.GET, "/api/v1/customers/" + CUSTOMER_ID + "/credit-cards/" + CREDIT_CARD_ID, "SCOPE_credit-cards:read", null, null),
                Arguments.of(HttpMethod.DELETE, "/api/v1/customers/" + CUSTOMER_ID + "/credit-cards/" + CREDIT_CARD_ID, "SCOPE_credit-cards:write", null, null)
        );
    }

    @ParameterizedTest(name = "{0} {1} sem token -> 401")
    @MethodSource("routes")
    void shouldRejectRequestWithoutToken(HttpMethod method, String path, String scope,
                                         String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("rota sem token deveria ser 401").isEqualTo(401));
    }

    @ParameterizedTest(name = "{0} {1} com escopo errado -> 403")
    @MethodSource("routes")
    void shouldRejectRequestWithUnrelatedScope(HttpMethod method, String path, String scope,
                                               String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body)
                        .with(jwt().authorities(new SimpleGrantedAuthority(UNRELATED_SCOPE))))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("token autenticado sem o escopo %s deveria ser 403", scope).isEqualTo(403));
    }

    @ParameterizedTest(name = "{0} {1} com {2} -> passa pela seguranca")
    @MethodSource("routes")
    void shouldAllowRequestWithRequiredScope(HttpMethod method, String path, String scope,
                                             String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body)
                        .with(jwt().authorities(new SimpleGrantedAuthority(scope))))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("com o escopo %s a requisicao nao deveria parar na seguranca", scope)
                        .isNotIn(401, 403));
    }

    /**
     * O webhook e publico por NECESSIDADE: o FastPay chama de fora e nao carrega token.
     * Este teste existe para que a excecao seja uma decisao visivel, e nao um efeito
     * colateral que alguem remove sem perceber a consequencia.
     *
     * O preco esta registrado como pendencia: este endpoint muda estado de fatura e hoje
     * nao verifica assinatura de origem nenhuma. Publico e sem verificacao sao duas
     * coisas diferentes - a primeira e necessaria aqui, a segunda nao deveria ser.
     */
    @Test
    void shouldNotRequireTokenOnFastpayWebhook() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/webhooks/fastpay")
                        .contentType(JSON).content("{}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("o webhook do gateway precisa continuar acessivel sem token")
                        .isNotIn(401, 403));
    }

    @ParameterizedTest(name = "{0} e publico")
    @ValueSource(strings = {"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"})
    void shouldNotRequireTokenOnHealthEndpoints(String path) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(path))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("%s nao deveria exigir token", path).isNotIn(401, 403));
    }

    private static MockHttpServletRequestBuilder request(HttpMethod method, String path,
                                                        String contentType, String body) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
        if (contentType != null) {
            builder.contentType(contentType).content(body == null ? "{}" : body);
        }
        return builder;
    }
}
