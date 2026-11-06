package com.algaworks.algashop.billing.application;

import lombok.Getter;

@Getter
public class ReplyPublishingException extends RuntimeException {

    private final OutboundIntegrationReply reply;

    // a causa vai junto: sem o super(..., e) o stack trace do broker sumiria do log
    public ReplyPublishingException(String message, OutboundIntegrationReply reply, Throwable e) {
        super("%s Reply=%s AggregateId=%s".formatted(message, reply.getClass().getSimpleName(), reply.getAggregateId()), e);
        this.reply = reply;
    }
}
