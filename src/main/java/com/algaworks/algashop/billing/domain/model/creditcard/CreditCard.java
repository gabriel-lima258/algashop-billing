package com.algaworks.algashop.billing.domain.model.creditcard;

import com.algaworks.algashop.billing.domain.model.util.IdGenerator;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.apache.commons.lang3.StringUtils;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/// /////////////////////////////
/// AGGREGATE ROOT
/// ////////////////////////////

@Setter(AccessLevel.PRIVATE)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
public class CreditCard {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;
    private OffsetDateTime createdAt;
    private UUID customerId;
    private String lastNumbers;
    private String brand;
    private Integer expMonth;
    private Integer expYear;

    private String gatewayCode;

    /// /////////////////////////////
    /// BUILDER FACTORY
    /// ////////////////////////////

    public static CreditCard brandNew(
            UUID customerId,
            String lastNumbers,
            String brand,
            Integer expMonth,
            Integer expYear,
            String gatewayCreditCardCode) {

        Objects.requireNonNull(customerId);
        Objects.requireNonNull(expMonth);
        Objects.requireNonNull(expYear);

        if (StringUtils.isAnyBlank(lastNumbers, brand, gatewayCreditCardCode)) {
            throw new IllegalArgumentException();
        }

        return new CreditCard(
                IdGenerator.generateTimeBasedUUID(),
                OffsetDateTime.now(),
                customerId,
                lastNumbers,
                brand,
                expMonth,
                expYear,
                gatewayCreditCardCode
        );
    }

    public void setGatewayCode(String gatewayCode) {
        if (StringUtils.isBlank(gatewayCode)) {
            throw new IllegalArgumentException();
        }
        this.gatewayCode = gatewayCode;
    }
}
