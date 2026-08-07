package com.algaworks.algashop.billing.application.invoice.management;

import com.algaworks.algashop.billing.domain.model.invoice.Invoice;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceNotFoundException;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceRepository;
import com.algaworks.algashop.billing.domain.model.invoice.InvoicingService;
import com.algaworks.algashop.billing.domain.model.invoice.payment.Payment;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// POR QUE ESTA CLASSE EXISTE: para que a chamada HTTP ao gateway aconteca FORA de transacao.
//
// Antes, o processPayment era @Transactional e chamava o Fastpay la dentro. A conexao do
// Hikari (pool default de 10) ficava presa durante toda a chamada. Com o gateway lento, dez
// faturas simultaneas esgotavam o pool e derrubavam o billing INTEIRO - inclusive o GET de
// fatura, que nem toca no gateway. Uma dependencia lenta virava indisponibilidade total.
//
// Nenhum circuit breaker corrige isso: quando a chamada chega no breaker, a conexao ja foi
// adquirida pelo @Transactional, que esta acima dele na pilha.
//
// Cada metodo aqui e uma transacao CURTA. O application service orquestra:
//     loadPaymentRequest (tx)  ->  capture (sem tx)  ->  assignPayment (tx)
//
// Classe separada porque autoinvocacao nao passa pelo proxy do Spring - se estes metodos
// ficassem no proprio application service, o @Transactional seria ignorado.
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoicePaymentTransactions {

    private final InvoiceRepository invoiceRepository;
    private final InvoicingService invoicingService;

    @Transactional(readOnly = true)
    public PaymentRequest loadPaymentRequest(UUID invoiceId) {
        Invoice invoice = findInvoice(invoiceId);

        return PaymentRequest.builder()
                .amount(invoice.getTotalAmount())
                .method(invoice.getPaymentSettings().getPaymentMethod())
                .creditCardId(invoice.getPaymentSettings().getCreditCardId())
                .payer(invoice.getPayer())
                .invoiceId(invoice.getId())
                .build();
    }

    @Transactional
    public void assignPayment(UUID invoiceId, Payment payment) {
        Invoice invoice = findInvoice(invoiceId);
        invoicingService.assignPayment(invoice, payment);
        invoiceRepository.saveAndFlush(invoice);
    }

    // NAO ha um cancel(invoiceId, reason) aqui, e a ausencia e o ponto.
    //
    // Ele existiu enquanto o processPayment cancelava a fatura ao falhar a integracao. Essa
    // regra caiu: timeout ou 5xx significam "nao sei se cobrou", e cancelar ai descartaria
    // uma fatura possivelmente paga. Sem aquele fluxo, o metodo ficou sem chamador.
    //
    // Cancelamento continua existindo - so que por decisao de NEGOCIO, dentro do agregado
    // (Invoice.updatePaymentStatus trata FAILED e REFUNDED), nao como reacao a falha de rede.
    private Invoice findInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId).orElseThrow(InvoiceNotFoundException::new);
    }
}
