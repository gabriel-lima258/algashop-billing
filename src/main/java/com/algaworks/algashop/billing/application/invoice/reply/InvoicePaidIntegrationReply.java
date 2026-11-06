package com.algaworks.algashop.billing.application.invoice.reply;

import com.algaworks.algashop.billing.application.OutboundIntegrationReply;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

// Resposta ao IssueInvoice: a fatura do pedido foi paga. E o que faz a saga sair de
// INVOICING. Nao confundir com o evento de mesmo nome, que e um anuncio sem destinatario.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvoicePaidIntegrationReply implements OutboundIntegrationReply {

    @NotNull
    private UUID invoiceId;

    @NotBlank
    private String orderId;

    @NotNull
    private OffsetDateTime paidAt;

    @Override
    public String getAggregateId() {
        return orderId;
    }
}