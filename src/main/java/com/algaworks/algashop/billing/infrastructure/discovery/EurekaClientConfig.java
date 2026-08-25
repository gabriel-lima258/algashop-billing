package com.algaworks.algashop.billing.infrastructure.discovery;

import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;

// classe para habilitar descoberta do client product dentro de eureka

@Configuration
@EnableDiscoveryClient
public class EurekaClientConfig {
}
