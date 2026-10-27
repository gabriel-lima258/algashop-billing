package com.algaworks.algashop.billing.infrastructure.listener;

import com.algaworks.algashop.billing.application.invoice.event.InvoiceIntegrationEventPublisher;
import com.algaworks.algashop.billing.application.invoice.event.InvoiceIssuedIntegrationEvent;
import com.algaworks.algashop.billing.application.invoice.event.InvoicePaidIntegrationEvent;
import com.algaworks.algashop.billing.application.util.Mapper;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceCanceledEvent;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceIssuedEvent;
import com.algaworks.algashop.billing.domain.model.invoice.InvoicePaidEvent;
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

    // escuta o commit em InvoiceIssuedEvent e envia o event ao publisher invoiceIntegrationEventPublisher
    @TransactionalEventListener
    public void listen(InvoiceIssuedEvent event) {
        log.info("InvoiceIssuedEvent listen 1");

        // faz o mapeamento da classe
        InvoiceIssuedIntegrationEvent integrationEvent = mapper.convert(event, InvoiceIssuedIntegrationEvent.class);
        // envia o evento
        invoiceIntegrationEventPublisher.send(integrationEvent);
    }

    @EventListener
    public void listen(InvoiceCanceledEvent event) {

    }

    @TransactionalEventListener
    public void listen(InvoicePaidEvent event) {
        InvoicePaidIntegrationEvent integrationEvent = mapper.convert(event, InvoicePaidIntegrationEvent.class);
        invoiceIntegrationEventPublisher.send(integrationEvent);
    }
}
