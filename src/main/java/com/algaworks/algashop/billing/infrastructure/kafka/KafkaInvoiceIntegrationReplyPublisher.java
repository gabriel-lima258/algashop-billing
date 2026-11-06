package com.algaworks.algashop.billing.infrastructure.kafka;

import com.algaworks.algashop.billing.application.EventPublishingException;
import com.algaworks.algashop.billing.application.OutboundIntegrationEvent;
import com.algaworks.algashop.billing.application.OutboundIntegrationReply;
import com.algaworks.algashop.billing.application.ReplyPublishingException;
import com.algaworks.algashop.billing.application.invoice.reply.InvoiceIntegrationReplyPublisher;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Envio MANUAL do reply. O Spring Kafka responde sozinho (@SendTo) quando a resposta sai
// no retorno do listener do comando; aqui o resultado so existe depois (captura ou
// webhook), em outro fluxo. Por isso o record e montado a mao: topico e correlationId vem
// do pending_reply, e o kafka_correlationId e o que liga esta resposta a saga no ordering.
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaInvoiceIntegrationReplyPublisher implements InvoiceIntegrationReplyPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Validator validator;

    @Override
    public void send(String replyChannel, String correlationId, OutboundIntegrationReply reply) {
        var violations = validator.validate(reply);
        Objects.requireNonNull(replyChannel);
        Objects.requireNonNull(correlationId);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        SendResult<String, Object> result = null;
        try {
            var record = new ProducerRecord<String, Object>(
                    replyChannel,
                    reply.getAggregateId(),
                    reply);

            record.headers().add(KafkaHeaders.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8));

            result = kafkaTemplate.send(record).get(40, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReplyPublishingException("Interrupted while publishing", reply, e);
        } catch (TimeoutException | ExecutionException | KafkaException e) {
            throw new ReplyPublishingException("Failed to publish", reply, e);
        }

        RecordMetadata metadata = result.getRecordMetadata();

        log.info("Published to {} {}-{} at offset {}",
                reply.getClass().getSimpleName(),
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
        );

    }

//    @Override
//    public void send(OutboundIntegrationEvent event) {
//        var violations = validator.validate(event);
//
//        if (!violations.isEmpty()) {
//            throw new ConstraintViolationException(violations);
//        }
//
//        SendResult<String, Object> result = null;
//        try {
//            ProducerRecord<String, Object> record = new ProducerRecord<>(
//                    properties.getInvoiceEventTopicName(),
//                    event.getAggregateId(),
//                    event);
//
//            if (event.getIdempotencyKey() != null) {
//                // publica a chave de idempotencia no header
//                record.headers().add(KafkaConfig.IDEMPOTENCY_KEY_HEADER, event.getIdempotencyKey().toString().getBytes());
//            }
//
//            result = kafkaTemplate.send(record).get(40, TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new EventPublishingException("Interrupted while publishing", event, e);
//        } catch (TimeoutException | ExecutionException | KafkaException e) {
//            throw new EventPublishingException("Failed to publish", event, e);
//        }
//
//        RecordMetadata metadata = result.getRecordMetadata();
//
//        log.info("Published to {} {}-{} at offset {}",
//                event.getClass().getSimpleName(),
//                metadata.topic(),
//                metadata.partition(),
//                metadata.offset()
//        );
//    }

}
