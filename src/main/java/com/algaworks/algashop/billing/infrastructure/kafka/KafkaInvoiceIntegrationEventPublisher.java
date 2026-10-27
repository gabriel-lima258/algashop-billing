package com.algaworks.algashop.billing.infrastructure.kafka;

import com.algaworks.algashop.billing.application.EventPublishingException;
import com.algaworks.algashop.billing.application.IntegrationEvent;
import com.algaworks.algashop.billing.application.invoice.event.InvoiceIntegrationEventPublisher;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaInvoiceIntegrationEventPublisher implements InvoiceIntegrationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AlgaShopMessagingKafkaProperties properties;
    private final Validator validator;

    @Override
    public void send(IntegrationEvent event) {
        var violations = validator.validate(event);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        SendResult<String, Object> result = null;
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    properties.getInvoiceEventTopicName(),
                    event.getAggregateId(),
                    event);

            if (event.getIdempotencyKey() != null) {
                // publica a chave de idempotencia no header
                record.headers().add(KafkaConfig.IDEMPOTENCY_KEY_HEADER, event.getIdempotencyKey().toString().getBytes());
            }

            result = kafkaTemplate.send(record).get(40, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishingException("Interrupted while publishing", event, e);
        } catch (TimeoutException | ExecutionException | KafkaException e) {
            throw new EventPublishingException("Failed to publish", event, e);
        }

        RecordMetadata metadata = result.getRecordMetadata();

        log.info("Published to {} {}-{} at offset {}",
                event.getClass().getSimpleName(),
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
        );
    }

}
