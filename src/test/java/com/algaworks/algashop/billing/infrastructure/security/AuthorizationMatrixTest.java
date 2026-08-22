package com.algaworks.algashop.billing.infrastructure.security;

import com.algaworks.algashop.billing.application.creditcard.management.CreditCardManagementService;
import com.algaworks.algashop.billing.application.creditcard.query.CreditCardQueryService;
import com.algaworks.algashop.billing.application.invoice.management.InvoiceManagementApplicationService;
import com.algaworks.algashop.billing.application.invoice.query.InvoiceQueryService;
import com.algaworks.algashop.billing.infrastructure.payment.fastpay.webhook.FastpayWebhookController;
import com.algaworks.algashop.billing.infrastructure.payment.fastpay.webhook.FastpayWebhookHandler;
import com.algaworks.algashop.billing.infrastructure.security.check.OAuth2SecurityCheckApplicationServiceImpl;
import com.algaworks.algashop.billing.presentation.InvoiceController;
import com.algaworks.algashop.billing.presentation.MyCreditCardController;
import com.algaworks.algashop.billing.presentation.MyInvoiceController;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * A matriz de autorizacao do billing: para CADA rota anotada, quem NAO pode (401/403)
 * e quem pode (passa pela seguranca).
 *
 * Tres publicos diferentes convivem aqui, e cada um tem o proprio grupo de rotas:
 *
 *   - POST /api/v1/orders/{id}/invoice: SO MAQUINA (client_credentials). Gerar fatura e
 *     fluxo interno de sistema; nem um MANAGER humano passa.
 *   - GET /api/v1/orders/{id}/invoice: escopo invoices:read E NAO ter papel CUSTOMER.
 *     Se um cliente comum pudesse consultar por orderId livre, viraria oraculo de
 *     faturas alheias - a role fecha esse risco.
 *   - Recursos /me (fatura propria e cartoes): escopo + papel CUSTOMER, nenhum
 *     customerId no path.
 *
 * A fatia importa a impl REAL do bean "securityCheck" porque a expressao SpEL de
 * @CanWriteInvoices o consulta; maquina e detectada por aud contendo o sub, e os
 * tokens sinteticos abaixo reproduzem isso.
 *
 * Ver o javadoc do AuthorizationMatrixTest do ordering para as decisoes de desenho
 * comuns aos servicos. Especifico do billing: a unica rota deliberadamente ABERTA do
 * sistema - o webhook do FastPay - fixada no teste do fim da classe.
 */
@WebMvcTest(controllers = {
        InvoiceController.class,
        MyInvoiceController.class,
        MyCreditCardController.class,
        FastpayWebhookController.class
})
@Import({BillingSecurityConfig.class, OAuth2SecurityCheckApplicationServiceImpl.class})
class AuthorizationMatrixTest {

    private static final String UNRELATED_SCOPE = "SCOPE_totally:unrelated";
    private static final String JSON = "application/json";

    private static final String USER_SUBJECT = "41cdc65c-6158-48b0-a8e6-34c0ff8fd74e";
    private static final String MACHINE_SUBJECT = "algashop-billing-m2m";
    private static final String AUDIENCE = "algashop-web";

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

    // o recurso /me nao carrega customerId no body: so o cartao tokenizado
    private static final String CREDIT_CARD_BODY = """
            {"tokenizedCard":"tok_abc123"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean private InvoiceManagementApplicationService invoiceManagementApplicationService;
    @MockitoBean private InvoiceQueryService invoiceQueryService;
    @MockitoBean private CreditCardManagementService creditCardManagementService;
    @MockitoBean private CreditCardQueryService creditCardQueryService;
    @MockitoBean private FastpayWebhookHandler fastpayWebhookHandler;

    // -------------------------------------------------------------------------
    // Tokens sinteticos
    // -------------------------------------------------------------------------

    /** Token de usuario: sub e UUID e NAO aparece na audience. */
    private static RequestPostProcessor userToken(String... authorities) {
        return jwt()
                .jwt(builder -> builder.subject(USER_SUBJECT).audience(List.of(AUDIENCE)))
                .authorities(Stream.of(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    /** Token de maquina (client_credentials): sub == client_id, presente na audience. */
    private static RequestPostProcessor machineToken(String... authorities) {
        return jwt()
                .jwt(builder -> builder.subject(MACHINE_SUBJECT)
                        .audience(List.of(MACHINE_SUBJECT, AUDIENCE)))
                .authorities(Stream.of(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    // -------------------------------------------------------------------------
    // Todas as rotas: sem token -> 401; escopo errado -> 403
    // -------------------------------------------------------------------------

    static Stream<Arguments> allRoutes() {
        return Stream.concat(myRoutes(), Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/orders/" + ORDER_ID + "/invoice", "SCOPE_invoices:write", JSON, INVOICE_BODY),
                Arguments.of(HttpMethod.GET, "/api/v1/orders/" + ORDER_ID + "/invoice", "SCOPE_invoices:read", null, null)
        ));
    }

    @ParameterizedTest(name = "{0} {1} sem token -> 401")
    @MethodSource("allRoutes")
    void shouldRejectRequestWithoutToken(HttpMethod method, String path, String scope,
                                         String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("rota sem token deveria ser 401").isEqualTo(401));
    }

    @ParameterizedTest(name = "{0} {1} com escopo errado -> 403")
    @MethodSource("allRoutes")
    void shouldRejectRequestWithUnrelatedScope(HttpMethod method, String path, String scope,
                                               String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body)
                        .with(userToken(UNRELATED_SCOPE, "ROLE_CUSTOMER")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("token autenticado sem o escopo %s deveria ser 403", scope).isEqualTo(403));
    }

    // -------------------------------------------------------------------------
    // Recursos /me: escopo + papel CUSTOMER
    // -------------------------------------------------------------------------

    static Stream<Arguments> myRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/customers/me/orders/" + ORDER_ID + "/invoice", "SCOPE_invoices:read", null, null),
                Arguments.of(HttpMethod.POST, "/api/v1/customers/me/credit-cards", "SCOPE_credit-cards:write", JSON, CREDIT_CARD_BODY),
                Arguments.of(HttpMethod.GET, "/api/v1/customers/me/credit-cards", "SCOPE_credit-cards:read", null, null),
                Arguments.of(HttpMethod.GET, "/api/v1/customers/me/credit-cards/" + CREDIT_CARD_ID, "SCOPE_credit-cards:read", null, null),
                Arguments.of(HttpMethod.DELETE, "/api/v1/customers/me/credit-cards/" + CREDIT_CARD_ID, "SCOPE_credit-cards:write", null, null)
        );
    }

    @ParameterizedTest(name = "{0} {1} com {2} mas sem papel CUSTOMER -> 403")
    @MethodSource("myRoutes")
    void shouldRejectMyRouteWithoutCustomerRole(HttpMethod method, String path, String scope,
                                                String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body)
                        .with(userToken(scope, "ROLE_MANAGER")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("recurso /me sem papel CUSTOMER deveria ser 403").isEqualTo(403));
    }

    @ParameterizedTest(name = "{0} {1} com token de maquina -> 403")
    @MethodSource("myRoutes")
    void shouldRejectMachineTokenOnMyRoutes(HttpMethod method, String path, String scope,
                                            String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body)
                        .with(machineToken(scope)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("token de maquina em recurso /me deveria ser 403").isEqualTo(403));
    }

    @ParameterizedTest(name = "{0} {1} com {2} e papel CUSTOMER -> passa")
    @MethodSource("myRoutes")
    void shouldAllowCustomerOnMyRoutes(HttpMethod method, String path, String scope,
                                       String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body)
                        .with(userToken(scope, "ROLE_CUSTOMER")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("CUSTOMER com o escopo %s nao deveria parar na seguranca", scope)
                        .isNotIn(401, 403));
    }

    // -------------------------------------------------------------------------
    // Leitura geral de fatura (por orderId): escopo + NAO ser CUSTOMER
    // -------------------------------------------------------------------------

    @Test
    void shouldRejectCustomerOnAdminInvoiceRead() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/orders/" + ORDER_ID + "/invoice")
                        .with(userToken("SCOPE_invoices:read", "ROLE_CUSTOMER")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("CUSTOMER na leitura geral de faturas deveria ser 403").isEqualTo(403));
    }

    @Test
    void shouldAllowNonCustomerOnAdminInvoiceRead() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/orders/" + ORDER_ID + "/invoice")
                        .with(userToken("SCOPE_invoices:read", "ROLE_MANAGER")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("MANAGER com invoices:read nao deveria parar na seguranca")
                        .isNotIn(401, 403));
    }

    // -------------------------------------------------------------------------
    // Geracao de fatura: SO maquina
    // -------------------------------------------------------------------------

    @Test
    void shouldRejectHumanUserOnInvoiceGeneration() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/v1/orders/" + ORDER_ID + "/invoice", JSON, INVOICE_BODY)
                        .with(userToken("SCOPE_invoices:write", "ROLE_MANAGER")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("usuario humano gerando fatura deveria ser 403, mesmo com o escopo")
                        .isEqualTo(403));
    }

    @Test
    void shouldAllowMachineOnInvoiceGeneration() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/api/v1/orders/" + ORDER_ID + "/invoice", JSON, INVOICE_BODY)
                        .with(machineToken("SCOPE_invoices:write")))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("maquina com invoices:write nao deveria parar na seguranca")
                        .isNotIn(401, 403));
    }

    // -------------------------------------------------------------------------
    // Rotas publicas
    // -------------------------------------------------------------------------

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
