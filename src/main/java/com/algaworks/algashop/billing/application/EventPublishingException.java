package com.algaworks.algashop.billing.application;

import lombok.Getter;

@Getter
public class EventPublishingException extends RuntimeException {

    private final OutboundIntegrationEvent event;

    // a causa vai junto: sem o super(..., e) o stack trace do broker sumiria do log
    public EventPublishingException(String message, OutboundIntegrationEvent event, Throwable e) {
        super("%s Event=%s AggregateId=%s".formatted(message, event.getClass().getSimpleName(), event.getAggregateId()), e);
        this.event = event;
    }
}
