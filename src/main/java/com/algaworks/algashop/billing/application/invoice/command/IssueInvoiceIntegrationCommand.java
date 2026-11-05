package com.algaworks.algashop.billing.application.invoice.command;

import com.algaworks.algashop.billing.application.InboundIntegrationCommand;
import com.algaworks.algashop.billing.application.order.snapshot.BillingSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.OrderItemSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.PaymentSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.ShippingSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Primeiro comando da saga: "billing, emita a fatura deste pedido". Comando, e nao
// evento: tem um destinatario (o billing) e espera uma resposta (fatura paga ou cancelada).
// Carrega o pedido inteiro congelado (ECST): itens, frete, cobranca e forma de pagamento.
// Assim o billing fatura sem chamar o ordering de volta. Key = orderId.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueInvoiceIntegrationCommand implements InboundIntegrationCommand {

    @NotBlank
    private String orderId;

    @NotNull
    private UUID customerId;

    @NotNull
    private OffsetDateTime placedAt;

    @NotNull
    @Positive
    private BigDecimal totalAmount;

    @Builder.Default
    @NotNull
    @Size(min = 1)
    @Valid
    private List<OrderItemSnapshot> items = new ArrayList<>();

    @NotNull
    @Valid
    private PaymentSnapshot payment;

    @NotNull
    @Valid
    private ShippingSnapshot shipping;

    @NotNull
    @Valid
    private BillingSnapshot billing;

}