package com.algaworks.algashop.billing.application.invoice;

import com.algaworks.algashop.billing.presentation.util.TestContainerPostgresSQLConfig;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestContainerPostgresSQLConfig.class)
public abstract class AbstractApplicationTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // inicia o postgres automaticamnete no docker e depois morre ao final do teste
//    @Container
//    @ServiceConnection // uma forma alternativa de criar dynamicSource de configs
//    protected static PostgreSQLContainer postgresSQLContainer = new PostgreSQLContainer<>("postgres:17-alpine")
//            .withDatabaseName("ordering_test");

}
