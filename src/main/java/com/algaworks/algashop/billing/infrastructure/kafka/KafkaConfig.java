package com.algaworks.algashop.billing.infrastructure.kafka;

import com.algaworks.algashop.billing.domain.model.DomainEntityNotFoundException;
import com.algaworks.algashop.billing.domain.model.DomainException;
import jakarta.validation.ConstraintViolationException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;
import java.util.Map;

// O centro da resiliencia do CONSUMO: define o que acontece quando um handler falha.
// Sem este arquivo, o default do spring-kafka era 10 tentativas sem intervalo e
// DESCARTE SILENCIOSO com offset commitado (a descoberta da Fase 39). Agora:
//
//   excecao no handler -> DefaultErrorHandler -> backoff exponencial 2s/4s/8s
//   (1 entrega original + 3 retentativas = 4 entregas) -> esgotou, ou a excecao e
//   nao-retryable (BusinessException, IllegalArgumentException - repetir nao muda
//   o resultado) -> DeadLetterPublishingRecoverer publica em ordering.dlt.<topico>,
//   NA MESMA PARTICAO do record original (preserva a ordem por key no DLT), com
//   headers de topico/particao/offset/excecao originais -> offset commitado, o
//   consumo segue. A mensagem nao se perde: fica 30 dias no DLT esperando alguem.
//
// Os topicos proprios do ordering (order.events / order.commands) e os tres DLTs
// tambem nascem aqui, com a mesma topologia do modulo: 3/3, min.insync.replicas=2.
@Configuration
public class KafkaConfig {

    private static final String DLT_PREFIX = "billing.dlt.";

    public static final String TYPE_ID_HEADER = "__TypeId__";
    public static final String IDEMPOTENCY_KEY_HEADER = "idempotency-key";

    private static final int TOPIC_PARTITIONS = 3;
    private static final int TOPIC_REPLICAS = 3;
    private static final long RETENTION_30_DAYS = Duration.ofDays(30).toMillis();

    // cria o topic de eventos produzidos em ordering
    @Bean
    public NewTopic invoiceEventTopic(AlgaShopMessagingKafkaProperties properties) {
        return TopicBuilder.name(properties.getInvoiceEventTopicName())
                .partitions(TOPIC_PARTITIONS) // o mesmo do topico original
                .replicas(TOPIC_REPLICAS)
                .configs(Map.of(
                        "min.insync.replicas", "2"
                ))
                .build();
    }

    @Bean
    public NewTopic invoiceEventsDlt(AlgaShopMessagingKafkaProperties properties) {
        return createDeadLetterTopic(properties.getInvoiceEventTopicName());
    }

    @Bean
    public NewTopic orderEventsDlt(AlgaShopMessagingKafkaProperties properties) {
        return createDeadLetterTopic(properties.getOrderEventTopicName());
    }

    // ignora eventos em retry em business exception e costumiza os valores de retries
    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        long minInterval = 2000L; // 2 segundos
        long maxInterval = 8000L; // 2 segundos
        long maxRetries = 3L; // 3 RETENTATIVAS: com a entrega original sao 4 execucoes (2s/4s/8s)
        double multiplier = 2; // multiplicador exponencial

        ExponentialBackOff exponentialBackOff = new ExponentialBackOff(minInterval, multiplier);
        exponentialBackOff.setMaxAttempts(maxRetries);
        exponentialBackOff.setMaxInterval(maxInterval);

        DefaultErrorHandler defaultErrorHandler = new DefaultErrorHandler(recoverer, exponentialBackOff);

        // não realiza retentativas dessas exceptions e envia para o dead letter topic
        defaultErrorHandler.addNotRetryableExceptions(
                DomainException.class,
                DomainEntityNotFoundException.class,
                ConstraintViolationException.class,
                DataIntegrityViolationException.class,
                IllegalArgumentException.class
        );

        return defaultErrorHandler;
    }

    private NewTopic createDeadLetterTopic(String sourceTopic) {
        return TopicBuilder.name(DLT_PREFIX + sourceTopic)
                .partitions(TOPIC_PARTITIONS)
                .replicas(TOPIC_REPLICAS)
                .configs(Map.of(
                        "min.insync.replicas", "2",
                        "retention.ms", String.valueOf(RETENTION_30_DAYS)))
                .build();
    }

    /**
     * Recoverer de Dead Letter Topic (DLT): é chamado pelo DefaultErrorHandler quando
     * um record esgota todas as retentativas (ou lança uma exceção não-retryable).
     * <p>
     * Funcionamento:
     * <ul>
     *   <li>Recebe o ConsumerRecord que falhou e a exceção que causou a falha.</li>
     *   <li>O resolver (lambda) decide o destino: mesmo nome do topic original com o
     *       prefixo {@code ordering.dlt.}, e a MESMA partição do record original,
     *       preservando a ordenação por chave.</li>
     *   <li>Publica o record no DLT via KafkaTemplate, adicionando headers com
     *       topic/partition/offset originais, classe e stacktrace da exceção.</li>
     *   <li>Após publicar, o handler faz commit do offset e o consumer segue para
     *       o próximo record, evitando que a partição fique travada.</li>
     * </ul>
     * Configurações:
     * <ul>
     *   <li>{@code setLogRecoveryRecord(true)}: loga o record enviado ao DLT.</li>
     *   <li>{@code setFailIfSendResultIsError(false)}: se o envio ao DLT falhar, o erro
     *       é apenas logado e o record é descartado, em vez de propagar a exceção e
     *       reprocessar o record indefinidamente.</li>
     * </ul>
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (consumerRecord, exception) ->
                        new TopicPartition(DLT_PREFIX + consumerRecord.topic(),
                        consumerRecord.partition())
        );
        recoverer.setLogRecoveryRecord(true);
        recoverer.setFailIfSendResultIsError(false);
        return recoverer;
    }

}
