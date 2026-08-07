package com.algaworks.algashop.billing.infrastructure.resilience;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

// classe para habilitar a configuração de resiliencia nativa do spring

@Configuration
@EnableResilientMethods
public class SpringResilienceConfig {
}
