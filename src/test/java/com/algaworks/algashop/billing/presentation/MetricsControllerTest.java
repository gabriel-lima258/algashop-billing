package com.algaworks.algashop.billing.presentation;

import com.algaworks.algashop.billing.application.metrics.MetricsQueryService;
import com.algaworks.algashop.billing.application.metrics.PaymentsOverviewOutput;
import com.algaworks.algashop.billing.infrastructure.security.BillingSecurityConfig;
import com.algaworks.algashop.billing.infrastructure.security.check.OAuth2SecurityCheckApplicationServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fatia web do relatorio de pagamentos: formato do mes no JSON, periodo opcional e mes
// invalido. A regra de acesso fica na AuthorizationMatrixTest.
@WebMvcTest(controllers = MetricsController.class)
@Import({BillingSecurityConfig.class, OAuth2SecurityCheckApplicationServiceImpl.class})
class MetricsControllerTest {

    private static final String URL = "/api/v1/metrics/payments-overview";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private MetricsQueryService metricsQueryService;

    @Test
    void shouldReturnOneItemPerMonthAndPaymentMethod() throws Exception {
        Mockito.when(metricsQueryService.getPaymentsOverview(YearMonth.of(2026, 7), YearMonth.of(2026, 7)))
                .thenReturn(List.of(
                        new PaymentsOverviewOutput(YearMonth.of(2026, 7), "CREDIT_CARD", 26, new BigDecimal("417107.93")),
                        new PaymentsOverviewOutput(YearMonth.of(2026, 7), "GATEWAY_BALANCE", 14, new BigDecimal("82773.96"))));

        mockMvc.perform(get(URL).param("yearMonthStart", "2026-07").param("yearMonthEnd", "2026-07")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_metrics:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].yearMonth").value("2026-07"))
                .andExpect(jsonPath("$[0].paymentMethod").value("CREDIT_CARD"))
                .andExpect(jsonPath("$[0].quantity").value(26))
                .andExpect(jsonPath("$[0].totalAmount").value(417107.93))
                .andExpect(jsonPath("$[1].paymentMethod").value("GATEWAY_BALANCE"));
    }

    // sem periodo, o controller repassa nulos e o servico decide o ano corrente
    @Test
    void shouldPassMissingPeriodToService() throws Exception {
        mockMvc.perform(get(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_metrics:read"))))
                .andExpect(status().isOk());

        Mockito.verify(metricsQueryService).getPaymentsOverview(null, null);
    }

    @Test
    void shouldAnswerBadRequestForInvalidMonth() throws Exception {
        mockMvc.perform(get(URL).param("yearMonthEnd", "2026-7")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_metrics:read"))))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(metricsQueryService);
    }
}
