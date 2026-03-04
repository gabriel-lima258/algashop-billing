package com.algaworks.algashop.billing.application.invoice.management;

import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardNotFoundException;
import com.algaworks.algashop.billing.domain.model.creditcard.CreditCardRepository;
import com.algaworks.algashop.billing.domain.model.invoice.*;
import com.algaworks.algashop.billing.domain.model.invoice.payment.Payment;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentGatewayService;
import com.algaworks.algashop.billing.domain.model.invoice.payment.PaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.misc.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Application Service responsável por orquestrar os casos de uso de faturamento (billing).
 *
 * Este serviço pertence à camada de Application e atua como um orquestrador:
 * - NÃO contém regras de negócio (essas ficam no domínio: InvoicingService, Invoice, etc.)
 * - Coordena a interação entre os objetos de domínio e infraestrutura
 * - Gerencia os limites da transação (@Transactional)
 * - Converte DTOs de entrada (Input) em objetos de domínio (Payer, LineItem, etc.)
 *
 * Fluxo geral do billing:
 * 1. generate() -> Cria uma fatura (Invoice) a partir dos dados do pedido
 * 2. processPayment() -> Processa o pagamento de uma fatura já criada via gateway
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceManagementApplicationService {

    // Serviço de infraestrutura que se comunica com o gateway de pagamento externo (ex: Stripe, PagSeguro)
    private final PaymentGatewayService paymentGatewayService;

    // Domain Service que contém a lógica de negócio para emitir faturas e atribuir pagamentos
    private final InvoicingService invoicingService;

    // Repositório para persistir e consultar faturas
    private final InvoiceRepository invoiceRepository;

    // Repositório para validar cartões de crédito do cliente
    private final CreditCardRepository creditCardRepository;

    /**
     * FLUXO 1: Gerar uma fatura (Invoice) para um pedido.
     *
     * Passo a passo:
     * 1. Valida se o cartão de crédito informado pertence ao cliente
     * 2. Converte os DTOs de entrada em objetos de domínio (Payer e LineItems)
     * 3. Delega ao InvoicingService a emissão da fatura (que calcula total, define status UNPAID, etc.)
     * 4. Configura o método de pagamento escolhido (cartão ou saldo do gateway)
     * 5. Persiste a fatura no banco e retorna o ID gerado
     *
     * Importante: a fatura é criada com status UNPAID. O pagamento efetivo
     * acontece depois, no método processPayment().
     */
    @Transactional
    public UUID generate(GenerateInvoiceInput input) {
        PaymentSettingsInput paymentSettingsInput = input.getPaymentSettings();

        // Passo 1: Garante que o cartão de crédito (se informado) realmente pertence ao cliente
        verififyCreditCardId(paymentSettingsInput.getCreditCardId(), input.getCustomerId());

        // Passo 2: Converte DTO PayerData -> objeto de domínio Payer (com Address embutido)
        Payer payer = convertToPayer(input.getPayer());

        // Passo 3: Converte DTOs LineItemInput -> objetos de domínio LineItem (com numeração sequencial)
        Set<LineItem> items = convertToLineItems(input.getItems());

        // Passo 4: Delega ao Domain Service a criação da Invoice
        // O InvoicingService.issue() aplica as regras de negócio: calcula totalAmount,
        // define issuedAt, expiresAt e status = UNPAID
        Invoice invoice = invoicingService.issue(input.getOrderId(), input.getCustomerId(), payer, items);

        // Passo 5: Define como o cliente vai pagar (CREDIT_CARD ou GATEWAY_BALANCE)
        invoice.changePaymentSettings(paymentSettingsInput.getMethod(), paymentSettingsInput.getCreditCardId());

        // Passo 6: Persiste a fatura no banco (saveAndFlush garante escrita imediata)
        invoiceRepository.saveAndFlush(invoice);

        return invoice.getId();
    }

    /**
     * FLUXO 2: Processar o pagamento de uma fatura existente.
     *
     * Passo a passo:
     * 1. Busca a fatura pelo ID (lança exceção se não encontrar)
     * 2. Monta o PaymentRequest com os dados da fatura
     * 3. Envia ao gateway de pagamento para capturar o valor
     * 4a. Se a captura FALHAR: cancela a fatura com a mensagem de erro
     * 4b. Se a captura tiver SUCESSO: atribui o pagamento à fatura via InvoicingService
     * 5. Persiste o estado atualizado da fatura
     *
     * Esse método seria chamado após o generate(), tipicamente por um listener
     * de evento ou um processo assíncrono que dispara o processamento do pagamento.
     */
    @Transactional
    public void processPayment(UUID invoiceId) {
        // Passo 1: Recupera a fatura do banco
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(InvoiceNotFoundException::new);

        // Passo 2: Extrai os dados necessários da fatura para montar a requisição ao gateway
        PaymentRequest paymentRequest = toPaymentRequest(invoice);

        // Passo 3: Tenta capturar o pagamento no gateway externo
        Payment payment;
        try {
            payment = paymentGatewayService.capture(paymentRequest);
        } catch (Exception e) {
            // Passo 4a (cenário de FALHA): Se o gateway lançar exceção,
            // cancela a fatura e registra o motivo do cancelamento
            String errorMessage = "Payment capture failded";
            log.error(errorMessage);
            invoice.cancel(errorMessage);
            invoiceRepository.saveAndFlush(invoice);
            return;
        }

        // Passo 4b (cenário de SUCESSO): Delega ao Domain Service atribuir o pagamento
        // O InvoicingService.assignPayment() marca a fatura como PAID, define paidAt, etc.
        invoicingService.assignPayment(invoice, payment);
        invoiceRepository.saveAndFlush(invoice);
    }

    /**
     * Monta o PaymentRequest a partir dos dados da Invoice.
     * Esse DTO é o contrato entre o Application Service e o PaymentGatewayService.
     */
    private PaymentRequest toPaymentRequest(Invoice invoice) {
        return PaymentRequest.builder()
                .amount(invoice.getTotalAmount())
                .method(invoice.getPaymentSettings().getPaymentMethod())
                .creditCardId(invoice.getPaymentSettings().getCreditCardId())
                .payer(invoice.getPayer())
                .invoiceId(invoice.getId())
                .build();
    }

    /**
     * Converte os DTOs de entrada (LineItemInput) em objetos de domínio (LineItem).
     * Cada item recebe uma numeração sequencial (1, 2, 3...) para ordenação na fatura.
     * Usa LinkedHashSet para preservar a ordem de inserção.
     */
    private Set<LineItem> convertToLineItems(Set<LineItemInput> items) {
        Set<LineItem> lineItems = new LinkedHashSet<>();
        int itemNumber = 1;
        for (LineItemInput itemInput: items) {
            lineItems.add(LineItem.builder()
                            .number(itemNumber)
                            .name(itemInput.getName())
                            .amount(itemInput.getAmount())
                    .build());
            itemNumber++;
        }

        return lineItems;
    }

    /**
     * Converte o DTO PayerData em o objeto de domínio Payer.
     * O Payer contém os dados pessoais do pagador + o endereço (Address value object).
     * Essa conversão isola a camada de domínio dos DTOs de entrada da aplicação.
     */
    private Payer convertToPayer(PayerData payer) {
        AddressData address = payer.getAddress();
        return Payer.builder()
                .fullName(payer.getFullName())
                .email(payer.getEmail())
                .document(payer.getDocument())
                .phone(payer.getPhone())
                .address(Address.builder()
                        .city(address.getCity())
                        .state(address.getState())
                        .neighborhood(address.getNeighborhood())
                        .complement(address.getComplement())
                        .zipCode(address.getZipCode())
                        .street(address.getStreet())
                        .number(address.getNumber())
                        .build())
                .build();
    }

    /**
     * Valida se o cartão de crédito pertence ao cliente.
     * Só verifica quando um creditCardId é informado (não-nulo).
     * Se o cartão não existir ou não pertencer ao cliente, lança CreditCardNotFoundException.
     */
    private void verififyCreditCardId(UUID creditCardId, @NotNull UUID customerId) {
        if (creditCardId != null && !creditCardRepository.existsByIdAndCustomerId(creditCardId, customerId)) {
            throw new CreditCardNotFoundException();
        }
    }
}
