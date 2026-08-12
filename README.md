# algashop-billing

Serviço de faturamento do AlgaShop: emite faturas a partir de pedidos, guarda cartões tokenizados e captura o pagamento no gateway.

É o contraponto deliberado ao [`algashop-ordering`](https://github.com/gabriel-lima258/algashop-ordering). Os dois resolvem domínios ricos; este resolve com **DDD pragmático**, e a comparação entre eles é mais instrutiva que qualquer um isolado.

---

## O problema

Cobrar envolve um terceiro que você não controla. O gateway pode demorar, pode cair, pode aprovar agora e estornar depois — e pode avisar disso **quando quiser**, por um webhook, muito depois de a requisição original ter terminado.

Isso muda o desenho. A fatura não é um registro que nasce pago ou não pago: ela é uma máquina de estados que recebe notícias de fora ao longo do tempo. Daí `Invoice` ter `markAsPaid()`, `cancel(reason)` e `updatePaymentStatus(...)` como métodos de negócio — e não setters.

---

## Stack

| | |
|---|---|
| **Java** | 25 |
| **Spring Boot** | 4.0.1 |
| **Banco** | PostgreSQL 17 (`billing`) |
| **Porta** | 8082 |
| **Pacote raiz** | `com.algaworks.algashop.billing` |
| **Schema** | Flyway — 1 migration |
| **Gateway** | FastPay (simulado localmente) |

A aplicação fixa `TimeZone` em **UTC** na inicialização, antes do `SpringApplication.run`. Fatura tem `expiresAt`, e vencimento que muda de significado conforme o fuso da máquina é uma classe inteira de bug que simplesmente não existe aqui.

---

## Arquitetura — DDD pragmático

Quatro camadas, sem a inversão formal de portas do `ordering`:

```
application/        casos de uso (management e query separados)
domain/model/       agregados, VOs, serviços de domínio
infrastructure/     persistência, integrações, listeners
presentation/       controllers e tratamento de erro
```

### A diferença que define os dois serviços

| | `ordering` — purista | `billing` — pragmático |
|---|---|---|
| Agregado | classe de domínio pura | **é** a entidade JPA (`@Entity`) |
| Persistência | assembler/disassembler entre domínio e entidade | Spring Data direto no agregado |
| Portas | interfaces `For…` em `core/ports` | repositórios e serviços injetados |
| Custo | mais classes, mais indireção | domínio acoplado ao ORM |
| Ganho | domínio testável sem infraestrutura | muito menos código para o mesmo comportamento |

O que **não** muda é o que importa: nos dois, a regra de negócio mora no agregado. `Invoice.cancel()` decide se pode cancelar; ninguém escreve `invoice.setStatus(CANCELED)` de fora.

A lição é que "DDD" descreve onde a regra mora, não quantas camadas existem. A separação agregado/entidade é uma técnica **a serviço** disso — útil quando o domínio é complexo o bastante para justificá-la, cerimônia quando não é.

---

## Modelo de domínio

### `Invoice` — agregado raiz

Estende `AbstractAuditableAggregateRoot`, que traz auditoria (`@CreatedBy`, `@CreatedDate`, `@LastModifiedBy`, `@LastModifiedDate`) e `@Version` para lock otimista.

```java
Invoice.issue(...)   // status UNPAID, expiresAt = agora + 3 dias, soma os itens,
                     // registra InvoiceIssuedEvent
```

| Método | O que decide |
|---|---|
| `markAsPaid()` | transição para `PAID` |
| `cancel(reason)` | transição para `CANCELED`, guardando o motivo |
| `assignPaymentGatewayCode(code)` | amarra a fatura ao pagamento no gateway |
| `changePaymentSettings(method, creditCardId)` | forma de pagamento |
| `updatePaymentStatus(status)` | traduz o que o gateway diz para o que o domínio faz |

O último é o coração: `FAILED` → cancela com "Payment failed", `REFUNDED` → cancela com "Payment refunded", `PAID` → marca como paga. O vocabulário do gateway não vaza para dentro do domínio.

### `CreditCard` — agregado

Guarda apenas o que é seguro guardar: `lastNumbers`, `brand`, `expMonth`, `expYear` e o `gatewayCode`. **O número do cartão nunca entra neste serviço** — quem o tokeniza é o gateway, e o que fica aqui é a referência.

### Value objects e enums

`Payer` · `Address` · `LineItem` · `Payment` · `PaymentRequest` · `LimitedCreditCard`

| Enum | Valores |
|---|---|
| `InvoiceStatus` | `PAID`, `UNPAID`, `CANCELED` |
| `PaymentMethod` | `CREDIT_CARD`, `GATEWAY_BALANCE` |
| `PaymentStatus` | `PENDING`, `PROCESSING`, `FAILED`, `REFUNDED`, `PAID` |

Repare que `PaymentStatus` (do gateway) e `InvoiceStatus` (do domínio) são **enums distintos**. Um pagamento em `PROCESSING` não tem status de fatura correspondente — e é `updatePaymentStatus` quem faz a ponte.

### Eventos

`InvoiceIssuedEvent` · `InvoicePaidEvent` · `InvoiceCanceledEvent`

---

## API

### Faturas — `/api/v1/orders/{orderId}/invoice`

| Verbo | Path | O que faz |
|---|---|---|
| `POST` | `/api/v1/orders/{orderId}/invoice` | emite a fatura e já tenta capturar o pagamento → `201` |
| `GET` | `/api/v1/orders/{orderId}/invoice` | busca a fatura do pedido |

O `POST` faz duas coisas em sequência: gera a fatura e processa o pagamento. Falha de gateway (`502`/`504`) é repropagada; as demais são logadas sem derrubar a emissão — a fatura existe mesmo que a cobrança tenha que ser tentada de novo.

### Cartões — `/api/v1/customers/{customerId}/credit-cards`

| Verbo | Path | O que faz |
|---|---|---|
| `POST` | `.../credit-cards` | registra um cartão já tokenizado → `201` |
| `GET` | `.../credit-cards` | lista os do cliente |
| `GET` | `.../credit-cards/{creditCardId}` | detalhe |
| `DELETE` | `.../credit-cards/{creditCardId}` | remove → `204` |

### Webhook — `/api/v1/webhooks/fastpay`

| Verbo | Path | O que faz |
|---|---|---|
| `POST` | `/api/v1/webhooks/fastpay` | recebe mudança de status vinda do gateway |

É o que fecha o ciclo. A captura é síncrona, mas estorno e falha posterior chegam por aqui — o gateway avisa, o handler traduz para `PaymentStatus` e chama `updatePaymentStatus` na fatura referenciada.

Erros seguem **RFC 7807** (`ProblemDetail`): `400` campos inválidos, `404` não encontrado, `422` regra de negócio, `502`/`504` falha de gateway.

---

## Integração com o FastPay

Dois clients declarativos (`@HttpExchange` sobre `RestClient`), ambos com interceptor que injeta o header `Token`:

| Client | Operações |
|---|---|
| `FastpayPaymentAPIClient` | capturar, consultar, estornar, cancelar pagamento |
| `FastpayCreditCardAPIClient` | criar, consultar, remover cartão |

### Trocando o gateway por um dublê

A propriedade `algashop.integrations.payment.provider` aceita `FASTPAY` ou `FAKE`, e as implementações são selecionadas por `@ConditionalOnProperty`:

| Porta | `FAKE` | `FASTPAY` |
|---|---|---|
| `PaymentGatewayService` | devolve sempre `PAID` | chama o gateway |
| `CreditCardProviderService` | cartão VISA fixo | tokeniza no gateway |

Falhas de rede são traduzidas na fronteira: `ResourceAccessException` → `GatewayTimeoutException` (504), `HttpServerErrorException` → `BadGatewayException` (502). O domínio nunca vê uma exceção de HTTP.

### Resiliência — dividida por idempotência

Aqui está a decisão que separa este serviço dos outros. Há **dois circuitos para o mesmo host**, e o critério não é a dependência — é o que a operação faz:

```
fastpayCB       findByCode, findById de cartão      COM retry
fastpayWriteCB  capture, create/delete de cartão    maxRetries(0)
```

> **O `capture` nunca é retentado.** Timeout num POST de cobrança **não cancela** a autorização do outro lado — significa *"não sei se cobrou"*, e repetir é o caminho clássico da cobrança dupla. Quem resolve a incerteza é a conciliação (webhook do gateway ou `findByCode`), não outra tentativa.

Por que não um circuito só, traduzindo as exceções do `capture` para tipos fora do `includes`? Funcionaria — e o "não retenta" passaria a depender de um detalhe silencioso da tradução, que qualquer refatoração futura reativaria sem ninguém perceber. **Em código que cobra dinheiro, explícito ganha de esperto.**

Pela mesma razão, o read timeout do pagamento é de **20 segundos**, e não dos 7 usados em leitura: cortar cedo não protege de nada — só aumenta a frequência do pior estado possível, que é não saber se cobrou.

E **falha de integração não cancela a fatura.** Ela fica `UNPAID` esperando conciliação; cancelar ali descartaria uma fatura possivelmente paga.

### O que resiliência não resolve

A mudança mais importante desta frente não é nenhum dos cinco padrões — é o `InvoicePaymentTransactions`:

```
loadPaymentRequest (tx)  →  capture (SEM tx)  →  assignPayment (tx)
```

Antes, `processPayment` era `@Transactional` e chamava o gateway lá dentro, segurando uma conexão do pool durante toda a ida. Dez faturas simultâneas com gateway lento esgotavam o pool e derrubavam o billing **inteiro**, inclusive endpoints que não tocam no gateway.

Nenhum circuit breaker corrige isso: quando a chamada chega ao breaker, a conexão **já foi adquirida** pelo `@Transactional`, que está acima dele na pilha.

---

## Como rodar

A partir do repositório [`algashop-meta`](https://github.com/gabriel-lima258/algashop-meta):

```bash
docker compose -f docker-compose.tools.yml up -d
```

Sobe o PostgreSQL na **5433** e o **FastPay** na **9995** — um gateway de pagamento simulado, fornecido como imagem pronta, com banco e massa próprios.

```bash
./gradlew bootRun
```

O serviço responde em `http://localhost:8082`. O Flyway cria o schema (`credit_card`, `payment_settings`, `invoice`, `invoice_line_item`) e carrega massa de teste.

---

## Testes

```bash
./gradlew test              # unitários
./gradlew integrationTest   # classes *IT
./gradlew check             # as duas
```

Os testes de integração sobem o **próprio PostgreSQL** via Testcontainers (`@ServiceConnection`), então não dependem do compose. O gateway é substituído por um **WireMock embarcado** na porta 8788, com stubs que reproduzem inclusive o ciclo de vida do cartão — criado, consultado, removido, e depois `404`.

O `FastpayResilienceIT` prova a garantia mais importante do serviço por **contagem de requests**:

```java
// uma única tentativa de cobrança
wireMockServer.verify(1, postRequestedFor(urlEqualTo(PAYMENTS_URL)));
```

Sem essa asserção, o teste passaria igual com retry ligado na cobrança.

Não há contract tests neste serviço.

---

## Imagem Docker

```bash
./gradlew bootJar
docker build -t algashop/billing:dev .
```

Ou multi-arquitetura com push:

```bash
./gradlew dockerBuild
```

A base é `eclipse-temurin:25-jre`, acompanhando o toolchain do `build.gradle`.


### Health check

```bash
curl -s localhost:8082/actuator/health | jq            # tudo
curl -s localhost:8082/actuator/health/readiness | jq  # só o essencial
```

O grupo `readiness` inclui **apenas o banco** — o circuito do FastPay fora do ar não tira a instância de rotação, só marca o serviço como `DEGRADED`. É um status inventado pelo projeto, posicionado entre `UNKNOWN` e `UP` no `status.order`.

> ⚠️ `DEGRADED` devolve **HTTP 200**: só `DOWN` e `OUT_OF_SERVICE` viram 503 por padrão. Um probe que olhe o código de status não vê diferença.

Detalhes em [Health check e degradação](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/health-checks.md).


### Segurança — escopos exigidos

Este serviço é um **resource server**: toda rota exige `Authorization: Bearer <jwt>`, validado contra o issuer `http://algashop-authorization-server:9000`. Só `/actuator/health/**` é público.

| Rota | Escopo |
|---|---|
| `GET /api/v1/orders/{orderId}/invoice` | `invoices:read` |
| `POST /api/v1/orders/{orderId}/invoice` | `invoices:write` |
| `GET` dos cartões do cliente | `credit-cards:read` |
| `POST`/`DELETE` de cartão | `credit-cards:write` |

**`/api/v1/webhooks/**` é público por necessidade** — o FastPay chama de fora e não carrega token. É a única rota deliberadamente aberta do sistema, e há um teste que **fixa** essa abertura para ninguém "consertá-la" e quebrar a confirmação de pagamento.

> ⚠️ O mesmo webhook muda estado de fatura e **não verifica origem nenhuma**. Público é necessário; sem verificação não deveria ser. Registrado como pendência.

```bash
TOKEN=$(curl -s -u algashop-test:testing123 -d grant_type=client_credentials \
  http://localhost:9000/oauth2/token | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" localhost:8082/api/v1/...
```

Sem token → **401**. Com token e sem o escopo → **403**. Detalhes em [Resource servers e escopos](https://github.com/gabriel-lima258/algashop-docs/blob/main/05-seguranca/resource-server-e-escopos.md).

---

## Documentação

Caderno de estudos do projeto: [`algashop-docs`](https://github.com/gabriel-lima258/algashop-docs). O que toca este serviço:

- [Arquitetura](https://github.com/gabriel-lima258/algashop-docs/blob/main/00-visao-geral/arquitetura.md) — como os quatro serviços se conectam
- [Tratamento de erros](https://github.com/gabriel-lima258/algashop-docs/blob/main/03-testes-integracao/tratamento-erros-api.md) — `ProblemDetail`, e por que 502/504 existem separados
- [Resiliência](https://github.com/gabriel-lima258/algashop-docs/blob/main/01-arquitetura-design/resiliencia.md) — os cinco padrões, e por que idempotência decide o que pode ser retentado
- [Health check e degradação](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/health-checks.md) — liveness × readiness e o status DEGRADED
- [Resource servers e escopos](https://github.com/gabriel-lima258/algashop-docs/blob/main/05-seguranca/resource-server-e-escopos.md) — escopo por rota, 401 × 403 e a matriz de testes
- [Resiliência na prática](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/resiliencia-config.md) — parâmetros, biblioteca e como testar
- [Contract tests e stubs](https://github.com/gabriel-lima258/algashop-docs/blob/main/03-testes-integracao/stubs-contract-tests.md) — WireMock e testes sem o outro serviço de pé
- [Flyway](https://github.com/gabriel-lima258/algashop-docs/blob/main/02-persistencia/flyway.md) — versionar schema como código
- [Ambiente local](https://github.com/gabriel-lima258/algashop-docs/blob/main/04-infraestrutura/ambiente-local.md) — portas, bancos e problemas comuns

O cancelamento automático de faturas vencidas não acontece aqui: é do [`algashop-billing-scheduler`](https://github.com/gabriel-lima258/algashop-billing-scheduler).
