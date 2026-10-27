package com.algaworks.algashop.billing.application.invoice.event;

import com.algaworks.algashop.billing.application.IntegrationEvent;

public interface InvoiceIntegrationEventPublisher {
    void send(IntegrationEvent event);
}
