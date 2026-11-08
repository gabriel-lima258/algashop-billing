package com.algaworks.algashop.billing.infrastructure.kafka;

import com.algaworks.algashop.billing.application.invoice.command.CancelInvoiceIntegrationCommand;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

// Sem Spring, Kafka ou banco: prova que o configurer registra o Validator no registrar e
// que esse Validator enxerga as constraints dos payloads do billing
class KafkaBeanValidationConfigurerTest {

    private LocalValidatorFactoryBean validator;
    private KafkaListenerEndpointRegistrar registrar;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        registrar = new KafkaListenerEndpointRegistrar();
        new KafkaBeanValidationConfigurer(validator).configureKafkaListeners(registrar);
    }

    @Test
    void shouldRegisterTheValidatorItReceived() {
        Assertions.assertThat(registrar.getValidator()).isSameAs(validator);
    }

    @Test
    void shouldPointTheViolationOfAnInvalidCommand() {
        Errors errors = validate(new CancelInvoiceIntegrationCommand(""));

        Assertions.assertThat(errors.hasErrors()).isTrue();
        Assertions.assertThat(errors.getFieldError("orderId")).isNotNull();
    }

    @Test
    void shouldAcceptAValidCommand() {
        Errors errors = validate(new CancelInvoiceIntegrationCommand("01K00000000000000000000000"));

        Assertions.assertThat(errors.hasErrors()).isFalse();
    }

    private Errors validate(Object payload) {
        Errors errors = new BeanPropertyBindingResult(payload, "payload");
        registrar.getValidator().validate(payload, errors);
        return errors;
    }
}
