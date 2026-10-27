package com.algaworks.algashop.billing.application;

import lombok.Getter;

@Getter
public class EventPublishingException extends RuntimeException {

    private final IntegrationEvent event;

    // a causa vai junto: sem o super(..., e) o stack trace do broker sumiria do log
    public EventPublishingException(String message, IntegrationEvent event, Throwable e) {
        super("%s Event=%s AggregateId=%s".formatted(message, event.getClass().getSimpleName(), event.getAggregateId()), e);
        this.event = event;
    }
}
