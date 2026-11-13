package com.algaworks.algashop.billing.presentation;

import com.algaworks.algashop.billing.application.metrics.MetricsQueryService;
import com.algaworks.algashop.billing.application.metrics.PaymentsOverviewOutput;
import com.algaworks.algashop.billing.infrastructure.security.check.SecurityAnnotations;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

// Metricas do billing para o painel admin: faturas pagas por mes e forma de pagamento.
// Mes fora do formato yyyy-MM nao converte e responde 400 antes de chegar ao servico.
// Sem periodo, o servico usa o ano corrente inteiro.
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsQueryService metricsQueryService;

    @SecurityAnnotations.CanReadMetrics
    @GetMapping("/payments-overview")
    public List<PaymentsOverviewOutput> getPaymentsOverview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonthStart,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonthEnd) {
        return metricsQueryService.getPaymentsOverview(yearMonthStart, yearMonthEnd);
    }
}
