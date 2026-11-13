package com.algaworks.algashop.billing.application.metrics;

import java.time.YearMonth;
import java.util.List;

// Consulta das metricas do billing. Interface na aplicacao e SQL na infraestrutura, o mesmo
// caminho do InvoiceQueryService (o billing nao usa portas de entrada e saida como o ordering).
public interface MetricsQueryService {
    List<PaymentsOverviewOutput> getPaymentsOverview(YearMonth yearMonthStart, YearMonth yearMonthEnd);
}
