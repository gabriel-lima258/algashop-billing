package com.algaworks.algashop.billing.presentation;

import com.algaworks.algashop.billing.application.invoice.query.InvoiceOutput;
import com.algaworks.algashop.billing.application.invoice.query.InvoiceQueryService;
import com.algaworks.algashop.billing.application.security.SecurityCheckApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.algaworks.algashop.billing.infrastructure.security.check.SecurityAnnotations.CanReadMyInvoices;

@RestController
@RequestMapping("/api/v1/customers/me/orders/{orderId}/invoice")
@RequiredArgsConstructor
public class MyInvoiceController {

    private final InvoiceQueryService invoiceQueryService;

    private final SecurityCheckApplicationService securityCheck;

    // o customerId nao vem do path: e o sub do token, e o filtro por cliente acontece
    // na PROPRIA consulta - fatura de outro cliente e indistinguivel de inexistente (404)
    @GetMapping
    @CanReadMyInvoices
    public InvoiceOutput findMyInvoice(@PathVariable String orderId) {
        UUID customerId = securityCheck.getAuthenticatedUserId();
        return invoiceQueryService.findByOrderIdAndCustomerId(orderId, customerId);
    }
}
