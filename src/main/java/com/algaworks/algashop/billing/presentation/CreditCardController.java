package com.algaworks.algashop.billing.presentation;

import com.algaworks.algashop.billing.application.creditcard.management.CreditCardManagementService;
import com.algaworks.algashop.billing.application.creditcard.management.TokenizedCreditCardInput;
import com.algaworks.algashop.billing.application.creditcard.query.CreditCardOutput;
import com.algaworks.algashop.billing.application.creditcard.query.CreditCardQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.algaworks.algashop.billing.infrastructure.security.SecurityAnnotations.*;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final CreditCardManagementService cardManagementService;
    private final CreditCardQueryService creditCardQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CanWriteCreditCards
    public CreditCardOutput register(@PathVariable UUID customerId, @RequestBody @Valid TokenizedCreditCardInput input) {
        input.setCustomerId(customerId);
        UUID creditCardId = cardManagementService.register(input);
        return creditCardQueryService.findOne(customerId, creditCardId);
    }

    @GetMapping
    @CanReadCreditCards
    public List<CreditCardOutput> findAllByCustomer(@PathVariable UUID customerId) {
        return creditCardQueryService.findByCustomer(customerId);
    }

    @GetMapping("/{creditCardId}")
    @CanReadCreditCards
    public CreditCardOutput findOneCustomer(@PathVariable UUID customerId, @PathVariable UUID creditCardId) {
        return creditCardQueryService.findOne(customerId, creditCardId);
    }

    @DeleteMapping("/{creditCardId}")
    @CanWriteCreditCards
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCreditCard(@PathVariable UUID customerId, @PathVariable UUID creditCardId) {
        cardManagementService.delete(customerId, creditCardId);
    }
}
