package com.algaworks.algashop.billing.infrastructure.kafka;

import com.algaworks.algashop.billing.application.invoice.command.CancelInvoiceIntegrationCommand;
import com.algaworks.algashop.billing.application.invoice.command.IssueInvoiceIntegrationCommand;
import com.algaworks.algashop.billing.application.invoice.management.GenerateInvoiceInput;
import com.algaworks.algashop.billing.application.invoice.management.GenerateInvoiceInputAssembler;
import com.algaworks.algashop.billing.application.invoice.management.InvoiceManagementApplicationService;
import com.algaworks.algashop.billing.application.invoice.query.InvoiceOutput;
import com.algaworks.algashop.billing.application.invoice.query.InvoiceQueryService;
import com.algaworks.algashop.billing.application.invoice.reply.InvoiceCanceledIntegrationReply;
import com.algaworks.algashop.billing.infrastructure.persistence.reply.PendingReplies;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Executa os comandos de fatura enviados pelo orquestrador (ordering). O comando so e
// aceito com kafka_correlationId e kafka_replyTopic: sem eles nao ha como responder, entao
// a falta vira IllegalArgumentException (nao-retryable) e a mensagem vai para o DLT.
// A resposta ao orquestrador ainda nao e enviada - vem na proxima etapa.
@Component
@Slf4j
@RequiredArgsConstructor
@KafkaListener(
        id = "#{algaShopMessagingKafkaProperties.invoiceCommandsConsumerGroup}",
        concurrency = "3",
        topics = "#{algaShopMessagingKafkaProperties.invoiceCommandsTopicName}")
public class KafkaInvoiceIntegrationCommandListener {

    private final InvoiceManagementApplicationService invoiceManagementApplicationService;
    private final InvoiceQueryService invoiceQueryService;
    private final GenerateInvoiceInputAssembler generateInvoiceInputAssembler;

    // guarda o endereco de resposta ate o pagamento se resolver
    private final PendingReplies pendingReplies;
    // uma transacao para fatura + pendente: os @Transactional de dentro participam dela
    private final TransactionTemplate transactionTemplate;

    @KafkaHandler
    public void handle(
            @Payload @Valid IssueInvoiceIntegrationCommand command,
            @Header(value = KafkaHeaders.CORRELATION_ID, required = false) byte[] correlationId,
            @Header(value = KafkaHeaders.REPLY_TOPIC, required = false) byte[] replyTopic,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        logReceived(command, messageKey, partition, offset);

        // verifica a ausencia desses valores no header
        String sagaCorrelationId = requireHeader(correlationId, KafkaHeaders.CORRELATION_ID);
        String sagaReplyTopic = requireHeader(replyTopic, KafkaHeaders.REPLY_TOPIC);

        GenerateInvoiceInput input = generateInvoiceInputAssembler.toGenerateInvoiceInput(command);

        // a resposta depende de um pagamento que ainda nao aconteceu (cobranca via
        // InvoiceIssued ou webhook), entao nao ha o que responder agora. Grava a fatura E o
        // pendente (correlacao + topico de resposta) numa transacao so: ou existem os dois,
        // ou nenhum - nunca uma fatura sem ter para onde responder
        transactionTemplate.executeWithoutResult(status -> {
            UUID invoiceId = invoiceManagementApplicationService.generate(input);
            pendingReplies.register(invoiceId, command.getOrderId(), sagaCorrelationId, sagaReplyTopic);
        });
    }

    // Compensacao: estorna e cancela a fatura. Aqui o reply e AUTOMATICO (@SendTo): o estorno
    // e sincrono, entao quando o metodo termina a fatura ja esta CANCELED e o resultado existe.
    // O retorno vira o reply, publicado pelo Spring no kafka_replyTopic do comando com o mesmo
    // kafka_correlationId. Contraste com o IssueInvoice acima: la o resultado so nasce depois do
    // pagamento, e o reply teve de ser manual (pending_reply)
    // Compensacao pedida pelo orquestrador: estornar a fatura. Aqui o reply e AUTOMATICO
    // (@SendTo): o estorno no gateway e sincrono, entao quando o metodo retorna a fatura ja
    // esta CANCELED e a resposta existe. O Spring publica o retorno no kafka_replyTopic do
    // comando e copia o kafka_correlationId. No IssueInvoice e diferente: o pagamento so se
    // resolve depois, e o reply sai manualmente via pending_reply.
    @KafkaHandler
    @SendTo
    public InvoiceCanceledIntegrationReply handle(
            @Payload @Valid CancelInvoiceIntegrationCommand command,
            @Header(value = KafkaHeaders.CORRELATION_ID, required = false) byte[] correlationId,
            @Header(value = KafkaHeaders.REPLY_TOPIC, required = false) byte[] replyTopic,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        logReceived(command, messageKey, partition, offset);

        // verifica a ausencia desses valores no header
        requireHeader(correlationId, KafkaHeaders.CORRELATION_ID);
        requireHeader(replyTopic, KafkaHeaders.REPLY_TOPIC);

        invoiceManagementApplicationService.refundByOrderId(command.getOrderId());
        InvoiceOutput invoiceOutput = invoiceQueryService.findByOrderId(command.getOrderId());

        return InvoiceCanceledIntegrationReply.builder()
                .invoiceId(invoiceOutput.getId())
                .orderId(invoiceOutput.getOrderId())
                .canceledAt(invoiceOutput.getCanceledAt())
                .build();
    }

    // o topico e exclusivo de comandos de fatura: tipo desconhecido e erro de contrato e
    // vai para o DLT. Listeners de EVENTO fazem o contrario (ignoram): evento novo do
    // produtor e evolucao normal; comando que o executor nao conhece nao e
    @KafkaHandler(isDefault = true)
    public void handle(
            @Payload Object command,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.warn("Unsupported invoice command: type={} key={} offset={}",
                command.getClass().getSimpleName(), messageKey, offset);
        throw new IllegalArgumentException(
                "Unsupported invoice command type " + command.getClass().getSimpleName());
    }

    private String requireHeader(byte[] value, String name) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("Invoice command without the " + name + " header");
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    private void logReceived(Object command, String messageKey, Integer partition, Long offset) {
        log.info("Received {} | key={} | partition={} | offset={} | thread={}",
                command.getClass().getSimpleName(), messageKey, partition, offset,
                Thread.currentThread().getName());
    }
}