package com.algaworks.algashop.billing.infrastructure.persistence.metrics;

import com.algaworks.algashop.billing.application.metrics.MetricsQueryService;
import com.algaworks.algashop.billing.application.metrics.PaymentsOverviewOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

// Relatorio tatico de pagamentos, numa consulta so no banco do proprio billing. O billing e
// o dono do fato "pagamento": o payment_method do pedido no ordering e so o que o cliente
// escolheu no checkout; se pagou, quando, e se foi cancelado depois, quem sabe e a fatura.
// Definicao do numero:
//   - pagamento = fatura PAID. A cancelada vira CANCELED mesmo se foi paga antes, entao o
//     status sozinho ja tira o "pago e cancelado";
//   - mes = o do paid_at (pagamento), nao o da emissao;
//   - forma = payment_settings.payment_method (no curso a coluna se chama method);
//     cada (mes, forma) e uma linha;
//   - sem generate_series: o contrato quer so o que existe, sem linhas zeradas (o inverso do
//     sales-overview do ordering). Sem left join, o sum nunca e nulo.
//   - intervalo semiaberto [dia 1 do inicio, dia 1 do mes seguinte ao fim): paid_at e data e
//     hora, e comparar com a data do ultimo dia pararia na meia-noite e perderia o dia 31.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsQueryServiceImpl implements MetricsQueryService {

    private static final String PAYMENTS_OVERVIEW_SQL = """
            select to_char(date_trunc('month', i.paid_at), 'yyyy-mm') as year_month,
                   ps.payment_method,
                   count(i.id) as quantity,
                   sum(i.total_amount) as total_amount
            from invoice i
            inner join payment_settings ps on ps.id = i.payment_settings_id
            where i.status = 'PAID'
                and i.paid_at >= cast(:startDate as date)
                and i.paid_at < cast(:endDateExclusive as date)
            group by 1, 2
            order by 1, 2
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<PaymentsOverviewOutput> getPaymentsOverview(YearMonth yearMonthStart, YearMonth yearMonthEnd) {
        // sem periodo: o ano corrente inteiro, como no contrato
        if (yearMonthStart == null) {
            yearMonthStart = Year.now().atMonth(1);
        }
        if (yearMonthEnd == null) {
            yearMonthEnd = Year.now().atMonth(12);
        }
        LocalDate startDate = yearMonthStart.atDay(1);
        LocalDate endDateExclusive = yearMonthEnd.atEndOfMonth().plusDays(1);

        return jdbcTemplate.query(PAYMENTS_OVERVIEW_SQL,
                Map.of("startDate", startDate, "endDateExclusive", endDateExclusive),
                (rs, rowNum) -> new PaymentsOverviewOutput(
                        YearMonth.parse(rs.getString("year_month")),
                        rs.getString("payment_method"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("total_amount")
                )
        );
    }
}
