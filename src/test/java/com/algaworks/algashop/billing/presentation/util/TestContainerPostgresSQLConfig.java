package com.algaworks.algashop.billing.presentation.util;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration
public class TestContainerPostgresSQLConfig {

    // iniciando o container static para que possamos evitar o delay para levantar os testContainer nos testes
    // isso evita de criar diversas instancias de docker no testes
    private static PostgreSQLContainer postgreSQLContainer =
            new PostgreSQLContainer<>("postgres:17-alpine");

    // configurando o testContainer como Bean para que o Spring possa gerenciar
    // o ciclo de vida do banco e evitar o fechamento quando extends abstract class em outras classes
    @Bean
    @ServiceConnection
    protected static PostgreSQLContainer postgresSQLContainer() {
        return postgreSQLContainer;
    }

}
