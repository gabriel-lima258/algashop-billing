package com.algaworks.algashop.billing.infrastructure.kafka;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@Data
@ConfigurationProperties("algashop.messaging.kafka")
public class AlgaShopMessagingKafkaProperties {

    @NotBlank
    private String orderEventTopicName;

    @NotBlank
    private String invoiceEventTopicName;

    @NotBlank
    private String orderEventsConsumerGroup;

    @NotBlank
    private String invoiceEventsConsumerGroup;
}
