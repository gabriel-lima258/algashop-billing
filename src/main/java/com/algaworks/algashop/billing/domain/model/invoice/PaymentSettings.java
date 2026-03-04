package com.algaworks.algashop.billing.domain.model.invoice;

import com.algaworks.algashop.billing.domain.model.BusinessException;
import com.algaworks.algashop.billing.domain.model.util.IdGenerator;
import jakarta.persistence.*;
import org.apache.commons.lang3.StringUtils;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

/// /////////////////////////////
/// ENTITY
/// ////////////////////////////

@Setter(AccessLevel.PRIVATE)
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class PaymentSettings {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;
    private UUID creditCardId;
    private String gatewayCode;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    // protegendo a camada de invoice, para não sofrer alteraçoes terceiras
    // apenas referenciamos para ter persistencia ao hibernate, mas nunca um filho tem acesso ao pai
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PACKAGE)
    @OneToOne(mappedBy = "paymentSettings")
    private Invoice invoice;

    /// /////////////////////////////
    /// BUILDER FACTORY
    /// ////////////////////////////

    static PaymentSettings brandNew(PaymentMethod method, UUID creditCard) {
        Objects.requireNonNull(method);
        if (method.equals(PaymentMethod.CREDIT_CARD)) {
            Objects.requireNonNull(creditCard);
        }

        return new PaymentSettings(
                IdGenerator.generateTimeBasedUUID(),
                creditCard,
                null,
                method,
                null
        );
    }

    void assignGatewayCode(String gatewayCode) {
        if (StringUtils.isBlank(gatewayCode)) {
            throw new IllegalArgumentException();
        }
        if (this.getGatewayCode() != null) {
            throw new BusinessException("Gateway code already assigned");
        }
        setGatewayCode(gatewayCode);
    }
}
