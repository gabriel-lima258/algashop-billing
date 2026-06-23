package com.algaworks.algashop.billing.application.invoice;

import com.algaworks.algashop.billing.presentation.util.TestContainerPostgresSQLConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainerPostgresSQLConfig.class)
public abstract class AbstractApplicationTest {

    // inicia o postgres automaticamnete no docker e depois morre ao final do teste
//    @Container
//    @ServiceConnection // uma forma alternativa de criar dynamicSource de configs
//    protected static PostgreSQLContainer postgresSQLContainer = new PostgreSQLContainer<>("postgres:17-alpine")
//            .withDatabaseName("ordering_test");

}
