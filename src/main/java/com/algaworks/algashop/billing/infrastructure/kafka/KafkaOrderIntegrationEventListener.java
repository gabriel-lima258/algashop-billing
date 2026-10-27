package com.algaworks.algashop.billing.infrastructure.kafka;

import com.algaworks.algashop.billing.application.invoice.management.GenerateInvoiceInput;
import com.algaworks.algashop.billing.application.invoice.management.GenerateInvoiceInputAssembler;
import com.algaworks.algashop.billing.application.invoice.management.InvoiceManagementApplicationService;
import com.algaworks.algashop.billing.application.order.event.OrderPlacedIntegrationEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@KafkaListener(
        id = "#{algaShopMessagingKafkaProperties.orderEventsConsumerGroup}",
        concurrency = "3",
        topics = "#{algaShopMessagingKafkaProperties.orderEventTopicName}")
public class KafkaOrderIntegrationEventListener {

    private final InvoiceManagementApplicationService invoiceManagementApplicationService;
    private final GenerateInvoiceInputAssembler generateInvoiceInputAssembler;

    @KafkaHandler(isDefault = true)
    public void handle(
            @Payload Object event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.info("Event ignored: type={} key={} offset={}",
                event.getClass().getSimpleName(), messageKey, offset);
    }

    @KafkaHandler
    public void handle(
            @Payload @Valid OrderPlacedIntegrationEvent event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false)
            String messageKey,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION
                    , required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        logReceived(event, messageKey, partition, offset);

        // geramos um evento de emitir invoice após receber o evento de order placed
        GenerateInvoiceInput input = generateInvoiceInputAssembler.toGenerateInvoiceInput(event);
        invoiceManagementApplicationService.generate(input);
    }

    private void logReceived(Object event, String messageKey, Integer partition, Long offset) {
        log.info("Received {} | key={} | partition={} | offset={} | thread={}",
                event.getClass().getSimpleName(), messageKey, partition, offset,
                Thread.currentThread().getName());
    }

}
