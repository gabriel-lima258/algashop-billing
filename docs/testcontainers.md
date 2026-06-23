# Testcontainers no algashop-billing

## O que e Testcontainers?

Testcontainers e uma biblioteca Java que permite subir containers Docker descartaveis durante a execucao dos testes.
Em vez de depender de um banco de dados externo pre-configurado (como o PostgreSQL em `localhost:5433`), o Testcontainers cria automaticamente um container com a imagem do banco, executa os testes contra ele, e destrói o container ao final.

**Vantagens em relacao ao setup atual:**

| Aspecto | Atual (H2 + PostgreSQL fixo) | Com Testcontainers |
|---|---|---|
| Banco nos testes de integracao | PostgreSQL fixo em `localhost:5433` | Container PostgreSQL efemero |
| Banco nos testes unitarios | H2 em memoria | Pode manter H2 ou migrar tambem |
| Compatibilidade SQL | H2 pode divergir do PostgreSQL real | PostgreSQL real, mesmo dialeto da producao |
| Setup do ambiente | Requer `docker compose up` manual do banco | Automatico, sem dependencia externa |
| CI/CD | Precisa de servico PostgreSQL configurado | Apenas Docker disponivel |
| Flyway migrations | Podem passar no H2 e falhar no PostgreSQL | Testadas contra PostgreSQL real |

## Pre-requisitos

- Docker instalado e rodando na maquina
- Java 21+

## 1. Adicionar dependencias no `build.gradle`

```groovy
dependencies {
    // ... dependencias existentes ...

    // Testcontainers
    testImplementation 'org.testcontainers:testcontainers:1.21.1'
    testImplementation 'org.testcontainers:junit-jupiter:1.21.1'
    testImplementation 'org.testcontainers:postgresql:1.21.1'

    // Remover H2 se nao for mais necessario
    // testRuntimeOnly 'com.h2database:h2'  // <-- pode remover
}
```

## 2. Criar classe base para testes de integracao

Criar a classe `src/test/java/com/algaworks/algashop/billing/IntegrationTestBase.java`:

```java
package com.algaworks.algashop.billing;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
public abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("billing_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }
}
```

### Por que `@DynamicPropertySource`?

O Testcontainers mapeia a porta 5432 do container para uma porta aleatoria no host.
`@DynamicPropertySource` injeta a URL JDBC correta (com a porta dinamica) nas propriedades do Spring **antes** do contexto subir. Isso substitui os valores fixos do `application-test-env.yaml`.

### Por que `static` no container?

Com `@Container` em um campo `static`, o Testcontainers cria o container **uma vez por classe de teste** e compartilha entre todos os metodos `@Test`. Sem `static`, um novo container seria criado para **cada metodo**, tornando os testes muito lentos.

## 3. Abordagem alternativa: Singleton Container (recomendado)

Para evitar criar um container por classe de teste (mais rapido no CI), use o padrao singleton:

```java
package com.algaworks.algashop.billing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("billing_test")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }
}
```

Nesse padrao:
- O bloco `static {}` inicia o container **uma unica vez** para toda a suite de testes.
- O Testcontainers cuida de destruir o container via Ryuk (daemon que limpa containers orfaos).
- Nao e necessario usar `@Testcontainers` nem `@Container`.

## 4. Migrar os testes existentes

### Antes (teste atual com H2/PostgreSQL fixo):

```java
@SpringBootTest
@Transactional
class InvoiceManagementApplicationServiceIT {
    // ...
}
```

### Depois (com Testcontainers):

```java
@Transactional
class InvoiceManagementApplicationServiceIT extends IntegrationTestBase {
    // ... nenhuma outra mudanca necessaria
}
```

Os testes que ja herdam de `AbstractFastpayImplIT` precisam de uma cadeia de heranca:

```
IntegrationTestBase (Testcontainers + datasource)
  └── AbstractFastpayImplIT (WireMock para Fastpay API)
        └── PaymentGatewayServiceFastpayImplIT
        └── CreditCardProviderServiceFastpayImplIT
```

## 5. Atualizar `application-test-env.yaml`

O `application-test-env.yaml` pode ser simplificado, pois `@DynamicPropertySource` sobrescreve as propriedades de datasource em tempo de execucao:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

algashop:
  integrations:
    payment:
      provider: FASTPAY
      fastpay:
        hostname: http://localhost:8788
        private-token: SSEewtC6Ps5yJLdB6SJmh1bqJXgRbCdf8ocDj2hU
        public-token: tDBmh0ZiDrmaZ8BVQD7i9UYdcj9KtZUk
        webhook-url: http://host.docker.internal:8082/api/v1/webhooks/fastpay
```

As propriedades `spring.datasource.*` e `spring.flyway.url/user/password` foram removidas pois serao injetadas dinamicamente.

## 6. Remover H2 do `build.gradle`

Apos migrar todos os testes para Testcontainers, remova a dependencia:

```groovy
// REMOVER esta linha:
// testRuntimeOnly 'com.h2database:h2'
```

Isso garante que nenhum teste rode acidentalmente contra H2 (que pode mascarar problemas de SQL especifico do PostgreSQL).

## 7. Executar os testes

```bash
# Testes unitarios (sem container, sem banco)
./gradlew test

# Testes de integracao (Testcontainers sobe o PostgreSQL automaticamente)
./gradlew integrationTest

# Todos
./gradlew check
```

Na primeira execucao, o Docker baixa a imagem `postgres:17-alpine` (~80MB). Execucoes seguintes usam cache local.

## 8. Troubleshooting

| Problema | Causa | Solucao |
|---|---|---|
| `Could not connect to Ryuk` | Docker nao esta rodando | Iniciar Docker Desktop |
| `Timed out waiting for container port` | Imagem ainda sendo baixada / firewall | Verificar `docker pull postgres:17-alpine` |
| Testes lentos | Um container por classe | Usar o padrao Singleton (secao 3) |
| Porta ja em uso | Nao se aplica | Testcontainers usa portas aleatorias |
| Flyway migration falha | SQL incompativel com PostgreSQL | Corrigir a migration (vantagem: agora voce descobre no teste, nao em producao) |

## Referencias

- [Testcontainers - Getting Started](https://java.testcontainers.org/)
- [Spring Boot Testcontainers Support](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Testcontainers PostgreSQL Module](https://java.testcontainers.org/modules/databases/postgres/)
