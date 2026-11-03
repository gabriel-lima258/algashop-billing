package com.algaworks.algashop.billing.application.stock.event;

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
public class StockReservationRejectedIntegrationEvent implements IntegrationEvent {

    @NotNull
    private UUID reservationId;

    @NotBlank
    private String orderId;

    @NotNull
    private OffsetDateTime rejectedAt;

    @Override
    public String getAggregateId() {
        return orderId;
    }
}
