package com.algaworks.algashop.billing.application.invoice.event;

import com.algaworks.algashop.billing.application.IntegrationEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceIssuedIntegrationEvent implements IntegrationEvent {

    @NotNull
    private UUID invoiceId;

    @NotBlank
    private String orderId;

    @NotNull
    private UUID customerId;

    @NotNull
    private OffsetDateTime issuedAt;

    @Override
    public String getAggregateId() {
        return invoiceId.toString();
    }

}
