package com.algaworks.algashop.billing.application.invoice.management;

import com.algaworks.algashop.billing.application.invoice.AbstractApplicationTest;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCard;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardRepository;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardTestDataBuilder;
import com.algaworks.algashop.billing.domain.model.invoice.*;
import com.algaworks.algashop.billing.presentation.BadGatewayException;
import com.algaworks.algashop.billing.domain.model.invoice.payment.Payment;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentGatewayService;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentRequest;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentStatus;
import com.algaworks.algashop.billing.infrastructure.listener.InvoiceEventListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBeans;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Teste de Integração do InvoiceManagementApplicationService.
 *
 * Testa os dois fluxos principais do billing:
 * - Geração de fatura (generate) com diferentes métodos de pagamento
 * - Processamento de pagamento (processPayment) via gateway
 *
 * Estratégia de teste:
 * - @SpringBootTest: sobe o contexto completo do Spring (container, JPA, banco H2, etc.)
 * - @Transactional: cada teste roda dentro de uma transação que é revertida ao final,
 *   garantindo isolamento entre testes (um teste não polui o banco do outro)
 * - @MockitoSpyBean no InvoicingService: usa o bean REAL mas permite verificar chamadas
 *   (spy = bean real + capacidade de verify/when do Mockito)
 * - @MockitoBean no PaymentGatewayService: substitui o bean real por um mock completo,
 *   porque não queremos chamar um gateway de pagamento externo em testes
 */
class InvoiceManagementApplicationServiceIT extends AbstractApplicationTest {

    // O serviço que estamos testando - injetado pelo Spring com todas as dependências reais
    @Autowired
    private InvoiceManagementApplicationService applicationService;

    // Repositórios reais (conectados ao banco H2 em memória) para preparar dados
    // e verificar o estado final após a execução
    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CreditCardRepository creditCardRepository;

    // SpyBean = bean REAL do Spring, mas "espionado" pelo Mockito.
    // Permite que a lógica real de negócio execute normalmente,
    // e depois verificamos se o método issue() foi chamado (Mockito.verify)
    @MockitoSpyBean
    private InvoicingService invoicingService;

    @MockitoSpyBean
    private InvoiceEventListener invoiceEventListener;

    // MockBean = substitui o bean real por um mock COMPLETO.
    // O PaymentGatewayService é mockado porque é uma integração externa (gateway de pagamento).
    // Em testes, controlamos o que ele retorna com Mockito.when()
    @MockitoBean
    private PaymentGatewayService paymentGatewayService;

    /**
     * TESTE 1: Gerar fatura com pagamento via CARTÃO DE CRÉDITO.
     *
     * Fluxo testado:
     * 1. Cria um cartão de crédito no banco (pré-condição)
     * 2. Monta o input com dados do pedido + pagador + cartão
     * 3. Chama applicationService.generate()
     * 4. Verifica TUDO que foi persistido: status, timestamps, valores, pagador, endereço, itens
     *
     * Este teste é o mais completo porque valida toda a cadeia de conversão:
     * DTO de entrada -> objetos de domínio -> persistência -> consulta do resultado
     */
    @Test
    void shouldGenerateInvoiceWithCreditCardAsPayment() {
        // === ARRANGE (preparação dos dados) ===

        // Cria um customerId aleatório e persiste um cartão de crédito vinculado a ele.
        // Isso é necessário porque o generate() valida se o cartão pertence ao cliente.
        UUID customerId = UUID.randomUUID();
        CreditCard creditCard = CreditCardTestDataBuilder.aCreditCard()
                .customerId(customerId)
                .build();
        creditCardRepository.saveAndFlush(creditCard);

        // Usa o TestDataBuilder para criar um input com dados padrão (pagador, itens, etc.)
        // e sobrescreve o customerId para coincidir com o do cartão
        GenerateInvoiceInput input = GenerateInvoiceInputTestDataBuilder.anInput()
                .customerId(customerId)
                .build();

        // Define o método de pagamento como CREDIT_CARD e associa o cartão criado acima
        input.setPaymentSettings(
                PaymentSettingsInput.builder()
                        .creditCardId(creditCard.getId())
                        .method(PaymentMethod.CREDIT_CARD)
                        .build()
        );

        // === ACT (execução do fluxo) ===

        // Chama o generate() que percorre o fluxo:
        // validação do cartão -> conversão de DTOs -> InvoicingService.issue() -> persistência
        UUID invoiceId = applicationService.generate(input);

        // === ASSERT (verificações) ===

        // Recupera a fatura do banco para verificar o que foi realmente persistido
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();

        // Verifica que a fatura foi criada com status UNPAID (ainda não foi paga)
        // e que os timestamps de emissão e vencimento foram definidos pelo InvoicingService
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoice.getIssuedAt()).isNotNull();
        assertThat(invoice.getExpiresAt()).isNotNull();
        assertThat(invoice.getExpiresAt()).isAfter(invoice.getIssuedAt());
        // Fatura recém-criada não deve ter data de pagamento nem cancelamento
        assertThat(invoice.getPaidAt()).isNull();
        assertThat(invoice.getCanceledAt()).isNull();
        assertThat(invoice.getCancelReason()).isNull();

        // Verifica que o orderId e customerId foram corretamente associados
        assertThat(invoice.getOrderId()).isEqualTo(input.getOrderId());
        assertThat(invoice.getCustomerId()).isEqualTo(customerId);

        // Verifica que o totalAmount foi calculado a partir dos itens
        // (o TestDataBuilder define 1 item de R$200,00)
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));

        // Verifica as configurações de pagamento
        PaymentSettings paymentSettings = invoice.getPaymentSettings();
        assertThat(paymentSettings).isNotNull();
        assertThat(paymentSettings.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(paymentSettings.getCreditCardId()).isEqualTo(creditCard.getId());
        // gatewayCode é null porque a fatura ainda não foi processada pelo gateway
        assertThat(paymentSettings.getGatewayCode()).isNull();

        // auditoria
        assertThat(invoice.getVersion()).isEqualTo(0L);
        assertThat(invoice.getCreatedAt()).isNotNull();
        // O autor vem do "sub" do token. Este IT roda SEM autenticação, então o
        // AuditorAware devolve Optional.empty() e a coluna fica nula - de propósito.
        // Antes da Fase 25 o provider devolvia UUID.randomUUID(), que era sempre não-nulo
        // e nunca identificou ninguém; a asserção antiga passava por acidente.
        assertThat(invoice.getCreatedByUserId()).isNull();

        // Verifica que o InvoicingService.issue() foi realmente chamado (usando o spy)
        // Isso garante que a lógica de negócio de emissão foi executada
        Mockito.verify(invoicingService).issue(any(), any(), any(), any());

        // verifica eventos
        Mockito.verify(invoiceEventListener).listen(Mockito.any(InvoiceIssuedEvent.class));
    }

    /**
     * TESTE 2: Gerar fatura com pagamento via SALDO DO GATEWAY (GATEWAY_BALANCE).
     *
     * Similar ao teste anterior, mas usa um método de pagamento diferente.
     * Verifica que o sistema suporta múltiplos métodos de pagamento corretamente.
     * As assertions são mais enxutas pois o teste anterior já cobre os detalhes do pagador/itens.
     */
    @Test
    void shouldGenerateInvoiceWithGatewayBalanceAsPayment() {
        // === ARRANGE ===
        UUID customerId = UUID.randomUUID();
        CreditCard creditCard = CreditCardTestDataBuilder.aCreditCard()
                .customerId(customerId)
                .build();
        creditCardRepository.saveAndFlush(creditCard);

        GenerateInvoiceInput input = GenerateInvoiceInputTestDataBuilder.anInput()
                .customerId(customerId)
                .build();

        // Diferença principal: usa GATEWAY_BALANCE ao invés de CREDIT_CARD
        input.setPaymentSettings(
                PaymentSettingsInput.builder()
                        .creditCardId(creditCard.getId())
                        .method(PaymentMethod.GATEWAY_BALANCE)
                        .build()
        );

        // === ACT ===
        UUID invoiceId = applicationService.generate(input);

        // === ASSERT ===
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();

        // Mesmas verificações de status (fatura criada sempre começa UNPAID)
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(invoice.getIssuedAt()).isNotNull();
        assertThat(invoice.getExpiresAt()).isNotNull();
        assertThat(invoice.getPaidAt()).isNull();
        assertThat(invoice.getCanceledAt()).isNull();

        assertThat(invoice.getOrderId()).isEqualTo(input.getOrderId());
        assertThat(invoice.getCustomerId()).isEqualTo(customerId);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));

        // Verifica que o método de pagamento é GATEWAY_BALANCE (a diferença deste teste)
        PaymentSettings paymentSettings = invoice.getPaymentSettings();
        assertThat(paymentSettings).isNotNull();
        assertThat(paymentSettings.getPaymentMethod()).isEqualTo(PaymentMethod.GATEWAY_BALANCE);
        assertThat(paymentSettings.getCreditCardId()).isEqualTo(creditCard.getId());

        // auditoria
        assertThat(invoice.getVersion()).isEqualTo(0L);
        assertThat(invoice.getCreatedAt()).isNotNull();
        // sem autenticação no IT, o autor é nulo (ver comentário no teste anterior)
        assertThat(invoice.getCreatedByUserId()).isNull();

        Mockito.verify(invoicingService).issue(any(), any(), any(), any());
    }

    /**
     * TESTE 3: Processar o pagamento de uma fatura existente.
     *
     * Fluxo testado (corresponde ao método processPayment do service):
     * 1. Cria uma fatura já emitida (status UNPAID) diretamente no banco
     * 2. Configura o mock do gateway para retornar um pagamento com sucesso
     * 3. Chama applicationService.processPayment()
     * 4. Verifica que a fatura mudou para PAID
     * 5. Verifica que o gateway e o InvoicingService foram chamados corretamente
     *
     * Aqui o @MockitoBean do PaymentGatewayService é essencial:
     * controlamos exatamente o que o "gateway" retorna, sem chamada externa real.
     */
    @Test
    void shouldProcessInvoicePayment() {
        // === ARRANGE ===

        // Cria uma fatura usando o TestDataBuilder e define o método de pagamento
        // Essa fatura simula uma fatura que já passou pelo generate() anteriormente
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().build();
        invoice.changePaymentSettings(PaymentMethod.GATEWAY_BALANCE, null);
        invoiceRepository.saveAndFlush(invoice);

        // Cria o Payment que o gateway mock vai retornar (simulando sucesso do pagamento)
        Payment payment = Payment.builder()
                .gatewayCode("12345")
                .invoiceId(invoice.getId())
                .method(invoice.getPaymentSettings().getPaymentMethod())
                .status(PaymentStatus.PAID)
                .build();

        // Configura o mock: quando o gateway.capture() for chamado com qualquer PaymentRequest,
        // retorna o payment de sucesso criado acima
        Mockito.when(paymentGatewayService.capture(Mockito.any(PaymentRequest.class))).thenReturn(payment);

        // === ACT ===

        // Executa o processamento do pagamento (fluxo 2 do service)
        applicationService.processPayment(invoice.getId());

        // === ASSERT ===

        // Recupera a fatura atualizada e verifica que mudou para PAID
        Invoice paidInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        Assertions.assertThat(paidInvoice.isPaid()).isTrue();

        // Verifica que o gateway foi chamado para capturar o pagamento
        Mockito.verify(paymentGatewayService).capture(Mockito.any(PaymentRequest.class));

        // Verifica que o InvoicingService.assignPayment() foi chamado para atribuir
        // o pagamento à fatura (esse método aplica as regras de negócio: marca como PAID, etc.)
        Mockito.verify(invoicingService).assignPayment(Mockito.any(Invoice.class), Mockito.any(Payment.class));

        // verifica eventos
        Mockito.verify(invoiceEventListener).listen(Mockito.any(InvoicePaidEvent.class));
    }

    @Test
    void shouldProcessInvoicePaymentAndCancelInvoice() {
        // === ARRANGE ===

        // Cria uma fatura usando o TestDataBuilder e define o método de pagamento
        // Essa fatura simula uma fatura que já passou pelo generate() anteriormente
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().build();
        invoice.changePaymentSettings(PaymentMethod.GATEWAY_BALANCE, null);
        invoiceRepository.saveAndFlush(invoice);

        // Cria o Payment que o gateway mock vai retornar (simulando sucesso do pagamento)
        Payment payment = Payment.builder()
                .gatewayCode("12345")
                .invoiceId(invoice.getId())
                .method(invoice.getPaymentSettings().getPaymentMethod())
                .status(PaymentStatus.FAILED)
                .build();

        // Configura o mock: quando o gateway.capture() for chamado com qualquer PaymentRequest,
        // retorna o payment de sucesso criado acima
        Mockito.when(paymentGatewayService.capture(Mockito.any(PaymentRequest.class))).thenReturn(payment);

        // === ACT ===

        // Executa o processamento do pagamento (fluxo 2 do service)
        applicationService.processPayment(invoice.getId());

        // === ASSERT ===

        // Recupera a fatura atualizada e verifica que mudou para PAID
        Invoice paidInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        Assertions.assertThat(paidInvoice.isCanceled()).isTrue();

        // Verifica que o gateway foi chamado para capturar o pagamento
        Mockito.verify(paymentGatewayService).capture(Mockito.any(PaymentRequest.class));

        // Verifica que o InvoicingService.assignPayment() foi chamado para atribuir
        // o pagamento à fatura (esse método aplica as regras de negócio: marca como PAID, etc.)
        Mockito.verify(invoicingService).assignPayment(Mockito.any(Invoice.class), Mockito.any(Payment.class));

        // verifica eventos
        Mockito.verify(invoiceEventListener).listen(Mockito.any(InvoiceCanceledEvent.class));

    }

    /**
     * REGRA: falha de INTEGRACAO nao cancela a fatura.
     *
     * Timeout ou 5xx do gateway significam "nao sei se cobrou" - cancelar ai seria descartar
     * uma fatura possivelmente paga. A fatura fica pendente e a conciliacao resolve (webhook
     * do Fastpay ou consulta por findByCode).
     *
     * Nao confundir com o teste acima: la o gateway RESPONDE, com um Payment de status FAILED.
     * Isso e uma resposta de negocio determinística e cancela mesmo. Aqui o gateway nao
     * respondeu nada.
     */
    @Test
    void shouldNotCancelInvoiceWhenGatewayFails() {
        // === ARRANGE ===
        Invoice invoice = InvoiceTestDataBuilder.anInvoice().build();
        invoice.changePaymentSettings(PaymentMethod.GATEWAY_BALANCE, null);
        invoiceRepository.saveAndFlush(invoice);

        Mockito.when(paymentGatewayService.capture(Mockito.any(PaymentRequest.class)))
                .thenThrow(new BadGatewayException.ServerErrorException("Fastpay API Bad Gateway", null));

        // === ACT / ASSERT ===
        // a excecao propaga para o controller devolver 502
        Assertions.assertThatThrownBy(() -> applicationService.processPayment(invoice.getId()))
                .isInstanceOf(BadGatewayException.class);

        Invoice pendingInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        Assertions.assertThat(pendingInvoice.isCanceled()).isFalse();
        Assertions.assertThat(pendingInvoice.isPaid()).isFalse();

        // nao houve pagamento para atribuir
        Mockito.verify(invoicingService, Mockito.never())
                .assignPayment(Mockito.any(Invoice.class), Mockito.any(Payment.class));
    }

}