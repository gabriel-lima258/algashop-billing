package com.algaworks.algashop.billing.application.order.event;

import com.algaworks.algashop.billing.application.IntegrationEvent;
import com.algaworks.algashop.billing.application.order.snapshot.BillingSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.OrderItemSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.PaymentSnapshot;
import com.algaworks.algashop.billing.application.order.snapshot.ShippingSnapshot;
import com.algaworks.algashop.billing.domain.model.util.IdGenerator;
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

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderPlacedIntegrationEvent implements IntegrationEvent {

    @Builder.Default
    @NotNull
    private UUID idempotencyKey = IdGenerator.generateTimeBasedUUID();

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

    @Override
    public String getAggregateId() {
        return orderId;
    }
}