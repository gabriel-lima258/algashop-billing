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
    private String invoiceEventTopicName;

    // topico onde o orquestrador (ordering) envia os comandos de fatura
    @NotBlank
    private String invoiceCommandsTopicName;

    @NotBlank
    private String invoiceEventsConsumerGroup;

    // grupo que vai consumir esses comandos (o listener vem nas proximas aulas)
    @NotBlank
    private String invoiceCommandsConsumerGroup;

}
