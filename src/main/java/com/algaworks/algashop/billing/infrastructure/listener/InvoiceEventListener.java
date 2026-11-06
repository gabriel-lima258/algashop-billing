package com.algaworks.algashop.billing.infrastructure.listener;

import com.algaworks.algashop.billing.application.invoice.event.InvoiceCanceledIntegrationEvent;
import com.algaworks.algashop.billing.application.invoice.event.InvoiceIntegrationEventPublisher;
import com.algaworks.algashop.billing.application.invoice.event.InvoiceIssuedIntegrationEvent;
import com.algaworks.algashop.billing.application.invoice.event.InvoicePaidIntegrationEvent;
import com.algaworks.algashop.billing.application.invoice.reply.InvoiceCanceledIntegrationReply;
import com.algaworks.algashop.billing.application.invoice.reply.InvoiceIntegrationReplyPublisher;
import com.algaworks.algashop.billing.application.invoice.reply.InvoicePaidIntegrationReply;
import com.algaworks.algashop.billing.application.util.Mapper;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceCanceledEvent;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceIssuedEvent;
import com.algaworks.algashop.billing.domain.model.invoice.InvoicePaidEvent;
import com.algaworks.algashop.billing.infrastructure.persistence.reply.PendingReplies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class InvoiceEventListener {

    // efeito colateral do evento - interface, isola da implementação
    private final InvoiceIntegrationEventPublisher invoiceIntegrationEventPublisher;
    // mapper
    private final Mapper mapper;

    // reply
    private final PendingReplies pendingReplies;
    private final InvoiceIntegrationReplyPublisher invoiceIntegrationReplyPublisher;

    // escuta o commit em InvoiceIssuedEvent e envia o event ao publisher invoiceIntegrationEventPublisher
    @TransactionalEventListener
    public void listen(InvoiceIssuedEvent event) {
        log.info("InvoiceIssuedEvent listen 1");

        // faz o mapeamento da classe
        InvoiceIssuedIntegrationEvent integrationEvent = mapper.convert(event, InvoiceIssuedIntegrationEvent.class);
        // envia o evento
        invoiceIntegrationEventPublisher.send(integrationEvent);
    }

    @TransactionalEventListener
    public void listen(InvoiceCanceledEvent event) {
        log.info("InvoiceCanceledEvent listen 1");

        InvoiceCanceledIntegrationEvent integrationEvent = mapper.convert(event, InvoiceCanceledIntegrationEvent.class);
        invoiceIntegrationEventPublisher.send(integrationEvent);

        // se a fatura nasceu de um comando da saga, ha um pendente: responde ao orquestrador
        // e so depois apaga (at-least-once - se o envio falhar, o pendente fica para reenvio)
        pendingReplies.find(event.getInvoiceId()).ifPresent(pending -> {
            invoiceIntegrationReplyPublisher.send(
                    pending.getReplyTopic(),
                    pending.getCorrelationId(),
                    InvoiceCanceledIntegrationReply.builder()
                            .invoiceId(event.getInvoiceId())
                            .orderId(event.getOrderId())
                            .canceledAt(event.getCanceledAt())
                            .build()
            );
            // remove o pending apos consumir o evento
            pendingReplies.delete(event.getInvoiceId());
        });
    }

    @TransactionalEventListener
    public void listen(InvoicePaidEvent event) {
        log.info("InvoicePaidEvent listen 1");
        InvoicePaidIntegrationEvent integrationEvent = mapper.convert(event, InvoicePaidIntegrationEvent.class);
        invoiceIntegrationEventPublisher.send(integrationEvent);

        // se a fatura nasceu de um comando da saga, ha um pendente: responde ao orquestrador
        // e so depois apaga (at-least-once - se o envio falhar, o pendente fica para reenvio)
        pendingReplies.find(event.getInvoiceId()).ifPresent(pending -> {
            invoiceIntegrationReplyPublisher.send(
                    pending.getReplyTopic(),
                    pending.getCorrelationId(),
                    InvoicePaidIntegrationReply.builder()
                            .invoiceId(event.getInvoiceId())
                            .orderId(event.getOrderId())
                            .paidAt(event.getPaidAt())
                            .build()
            );
            // remove o pending apos consumir o evento
            pendingReplies.delete(event.getInvoiceId());
        });
    }
}
