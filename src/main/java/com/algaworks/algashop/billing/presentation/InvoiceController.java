package com.algaworks.algashop.billing.presentation;

import com.algaworks.algashop.billing.application.invoice.management.GenerateInvoiceInput;
import com.algaworks.algashop.billing.application.invoice.management.InvoiceManagementApplicationService;
import com.algaworks.algashop.billing.application.invoice.query.InvoiceOutput;
import com.algaworks.algashop.billing.application.invoice.query.InvoiceQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.algaworks.algashop.billing.infrastructure.security.check.SecurityAnnotations.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders/{orderId}/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceManagementApplicationService invoiceManagementApplicationService;
    private final InvoiceQueryService invoiceQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CanWriteInvoices
    public InvoiceOutput register(@PathVariable String orderId, @RequestBody @Valid GenerateInvoiceInput input) {
        input.setOrderId(orderId);
        UUID invoiceId = invoiceManagementApplicationService.generate(input);

        try {
            invoiceManagementApplicationService.processPayment(invoiceId);
        } catch (GatewayTimeoutException | BadGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error(String.format("Error when processing payment for invoice %s", invoiceId), e);
        }

        return invoiceQueryService.findByOrderId(orderId);
    }

    @GetMapping
    @CanReadInvoices
    public InvoiceOutput findByOrder(@PathVariable String orderId) {
        return invoiceQueryService.findByOrderId(orderId);
    }
}
