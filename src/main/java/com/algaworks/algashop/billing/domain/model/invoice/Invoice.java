package com.algaworks.algashop.billing.domain.model.invoice;

import com.algaworks.algashop.billing.domain.model.BusinessException;
import com.algaworks.algashop.billing.domain.model.util.IdGenerator;
import jakarta.persistence.*;
import org.apache.commons.lang3.StringUtils;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/// /////////////////////////////
/// AGGREGATE ROOT
/// ////////////////////////////

@Setter(AccessLevel.PRIVATE)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
public class Invoice {

    @EqualsAndHashCode.Include
    @Id
    private UUID id;
    private String orderId;
    private UUID customerId;

    private OffsetDateTime issuedAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime canceledAt;
    private OffsetDateTime expiresAt;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    // assim que o invoice sofrer delete ou persitencia essa classe tem efeito colateral junto
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private PaymentSettings paymentSettings;

    // lista de values objects
    @ElementCollection
    @CollectionTable(name = "invoice_line_item", joinColumns = @JoinColumn(name = "invoice_id"))
    private Set<LineItem> items = new HashSet<>();

    @Embedded
    private Payer payer;

    private String cancelReason;

    /// /////////////////////////////
    /// BUILDER FACTORY
    /// ////////////////////////////

    // estado inicial de uma fatura
    public static Invoice issue(String orderId, UUID customerId, Payer payer, Set<LineItem> items) {
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(payer);
        Objects.requireNonNull(items);

        if (StringUtils.isBlank(orderId)) {
            throw new IllegalArgumentException();
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException();
        }

        BigDecimal totalAmount = items.stream().map(LineItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Invoice(
                IdGenerator.generateTimeBasedUUID(),
                orderId,
                customerId,
                OffsetDateTime.now(),
                null,
                null,
                OffsetDateTime.now().plusDays(3),
                totalAmount,
                InvoiceStatus.UNPAID,
                null,
                items,
                payer,
                null
        );
    }

    // reescrevendo o items para que não possa ser modificado por açoões externas
    public Set<LineItem> getItems() {
        return Collections.unmodifiableSet(this.items);
    }

    public boolean isCanceled() {
        return InvoiceStatus.CANCELED.equals(this.getStatus());
    }

    public boolean isUnpaid() {
        return InvoiceStatus.UNPAID.equals(this.getStatus());
    }

    public boolean isPaid() {
        return InvoiceStatus.PAID.equals(this.getStatus());
    }

    public void markAsPaid() {
        if (!isUnpaid()) {
            throw new BusinessException(String.format("Invoice %s with status %s cannot be marked as paid",
                    this.getId(), this.getStatus().toString().toLowerCase()));
        }
        setPaidAt(OffsetDateTime.now());
        setStatus(InvoiceStatus.PAID);
    }

    public void cancel(String cancelReason) {
        if (isCanceled()) {
            throw new BusinessException(String.format("Invoice %s is already canceled", this.getId()));
        }

        setCancelReason(cancelReason);
        setCanceledAt(OffsetDateTime.now());
        setStatus(InvoiceStatus.CANCELED);
    }

    public void assignPaymentGatewayCode(String code) {
        if (!isUnpaid()) {
            throw new BusinessException(String.format("Invoice %s with status %s cannot be edited",
                    this.getId(), this.getStatus().toString().toLowerCase()));
        }
        this.getPaymentSettings().assignGatewayCode(code);
    }

    public void changePaymentSettings(PaymentMethod method, UUID creditCard) {
        if (!isUnpaid()) {
            throw new BusinessException(String.format("Invoice %s with status %s cannot be edited",
                    this.getId(), this.getStatus().toString().toLowerCase()));
        }
        PaymentSettings paymentSetting = PaymentSettings.brandNew(method, creditCard);
        // evita problemas de persistencia por causa do cascade
        paymentSetting.setInvoice(this);
        this.setPaymentSettings(paymentSetting);
    }
}
