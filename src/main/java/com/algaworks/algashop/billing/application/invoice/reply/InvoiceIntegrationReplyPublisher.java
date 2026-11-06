package com.algaworks.algashop.billing.application.invoice.reply;

import com.algaworks.algashop.billing.application.OutboundIntegrationReply;

// Porta de saida das respostas. Recebe o topico e a correlacao de fora (vem do pendente),
// porque quem decide para onde responder e o comando, nao o billing.
public interface InvoiceIntegrationReplyPublisher {
    void send(String replyChannel, String correlationId, OutboundIntegrationReply reply);
}
