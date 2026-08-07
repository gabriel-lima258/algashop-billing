package com.algaworks.algashop.billing.application.creditcard.management;

import com.algaworks.algashop.billing.domain.model.creditcard.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

// SEM @Transactional na classe, e isso e deliberado.
//
// Os dois metodos chamam o Fastpay. Com @Transactional, a conexao do banco ficaria presa
// durante a chamada HTTP inteira - gateway lento esgota o pool e derruba o servico todo
// (mesmo problema descrito em InvoicePaymentTransactions).
//
// Nao precisa de transacao explicita aqui: cada metodo faz UMA escrita, e os metodos do
// Spring Data ja sao transacionais por conta propria. Nao ha invariante que exija as duas
// operacoes (gateway + banco) no mesmo commit - e nem daria, ja que uma delas e HTTP.
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditCardManagementService {

    private final CreditCardRepository creditCardRepository;
    private final CreditCardProviderService creditCardProviderService;

    // Ordem: gateway PRIMEIRO, banco depois. Se o gateway falhar, nada foi gravado aqui e o
    // cliente pode tentar de novo. O risco residual e o inverso - gravacao local falhar
    // depois do cartao criado no gateway, deixando um token orfao la. Token orfao nao
    // referenciado por ninguem e inofensivo; o contrario (cartao local apontando para token
    // inexistente) quebraria o pagamento.
    public UUID register(TokenizedCreditCardInput input) {
        LimitedCreditCard limitedCreditCard = creditCardProviderService.register(
                input.getCustomerId(), input.getTokenizedCard());

        CreditCard creditCard = CreditCard.brandNew(
                input.getCustomerId(),
                limitedCreditCard.getLastNumbers(),
                limitedCreditCard.getBrand(),
                limitedCreditCard.getExpMonth(),
                limitedCreditCard.getExpYear(),
                limitedCreditCard.getGatewayCode()
        );

        creditCardRepository.saveAndFlush(creditCard);

        return creditCard.getId();
    }

    // Ordem: banco PRIMEIRO, gateway depois - o inverso do register, pelo mesmo criterio.
    //
    // Antes, com a chamada dentro da transacao, uma falha no gateway desfazia a remocao
    // local. Fora da transacao esse rollback nao existe mais, entao a ordem passa a importar:
    // se o gateway falhar, sobra um token orfao la (inofensivo) e o estado local esta certo.
    // Na ordem inversa, uma falha no banco deixaria o cliente com um cartao que nao paga.
    //
    // A excecao propaga (502/504) porque a limpeza no gateway de fato nao aconteceu - e
    // repetir o DELETE e seguro, ele e idempotente.
    public void delete(UUID customerId, UUID creditCardId) {
        CreditCard creditCard = creditCardRepository.findByCustomerIdAndId(customerId, creditCardId)
                .orElseThrow(() -> new CreditCardNotFoundException());

        creditCardRepository.delete(creditCard);
        creditCardProviderService.delete(creditCard.getGatewayCode());
    }

}
