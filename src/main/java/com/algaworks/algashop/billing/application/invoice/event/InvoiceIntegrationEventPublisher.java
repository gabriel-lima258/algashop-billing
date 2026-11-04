package com.algaworks.algashop.billing.application.invoice.event;

import com.algaworks.algashop.billing.application.OutboundIntegrationEvent;

public interface InvoiceIntegrationEventPublisher {
    void send(OutboundIntegrationEvent event);
}
