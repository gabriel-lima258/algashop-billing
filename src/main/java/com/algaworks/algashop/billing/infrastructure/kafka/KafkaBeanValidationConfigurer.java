package com.algaworks.algashop.billing.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

// Pluga o Bean Validation nos listeners Kafka: por padrao um @Valid em @Payload NAO
// valida nada - e preciso registrar um validator no endpoint registrar, e e exatamente
// isso que este KafkaListenerConfigurer faz. Com ele, o payload que viole as anotacoes
// (@NotNull, @NotBlank, @Size, @Positive) vira MethodArgumentNotValidException ANTES do
// metodo do handler rodar: a mensagem invalida nao entra no fluxo de negocio. O
// DefaultErrorHandler trata essa excecao como fatal - vai direto para a DLT, sem retry.
@Configuration
@RequiredArgsConstructor
public class KafkaBeanValidationConfigurer implements KafkaListenerConfigurer {

    private final LocalValidatorFactoryBean validator;

    @Override
    public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
        registrar.setValidator(validator);
    }
}
