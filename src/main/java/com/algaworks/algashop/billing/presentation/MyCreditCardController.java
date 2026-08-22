package com.algaworks.algashop.billing.presentation;

import com.algaworks.algashop.billing.application.creditcard.management.CreditCardManagementService;
import com.algaworks.algashop.billing.application.creditcard.management.TokenizedCreditCardInput;
import com.algaworks.algashop.billing.application.creditcard.query.CreditCardOutput;
import com.algaworks.algashop.billing.application.creditcard.query.CreditCardQueryService;
import com.algaworks.algashop.billing.application.security.SecurityCheckApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.algaworks.algashop.billing.infrastructure.security.check.SecurityAnnotations.*;

@RestController
@RequestMapping("/api/v1/customers/me/credit-cards")
@RequiredArgsConstructor
public class MyCreditCardController {

    private final CreditCardManagementService cardManagementService;
    private final CreditCardQueryService creditCardQueryService;

    private final SecurityCheckApplicationService securityCheck;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CanWriteMyCreditCards
    public CreditCardOutput register(@RequestBody @Valid TokenizedCreditCardInput input) {
        input.setCustomerId(getUser());
        UUID creditCardId = cardManagementService.register(input);
        return creditCardQueryService.findOne(getUser(), creditCardId);
    }

    @GetMapping
    @CanReadMyCreditCards
    public List<CreditCardOutput> findAllByCustomer() {
        return creditCardQueryService.findByCustomer(getUser());
    }

    @GetMapping("/{creditCardId}")
    @CanReadMyCreditCards
    public CreditCardOutput findOneCustomer(@PathVariable UUID creditCardId) {
        return creditCardQueryService.findOne(getUser(), creditCardId);
    }

    @DeleteMapping("/{creditCardId}")
    @CanWriteMyCreditCards
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCreditCard(@PathVariable UUID creditCardId) {
        cardManagementService.delete(getUser(), creditCardId);
    }

    private UUID getUser() {
        return securityCheck.getAuthenticatedUserId();
    }
}
