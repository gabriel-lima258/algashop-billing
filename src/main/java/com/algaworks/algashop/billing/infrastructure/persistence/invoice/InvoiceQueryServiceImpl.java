package com.algaworks.algashop.billing.infrastructure.persistence.invoice;

import com.algaworks.algashop.billing.application.invoice.query.InvoiceOutput;
import com.algaworks.algashop.billing.application.invoice.query.InvoiceQueryService;
import com.algaworks.algashop.billing.application.util.Mapper;
import com.algaworks.algashop.billing.domain.model.invoice.Invoice;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceNotFoundException;
import com.algaworks.algashop.billing.domain.model.invoice.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InvoiceQueryServiceImpl implements InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;
    private final Mapper mapper;

    @Override
    public InvoiceOutput findByOrderId(String orderId) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId).orElseThrow(InvoiceNotFoundException::new);
        // mapeamos o data de output para domain
        return mapper.convert(invoice, InvoiceOutput.class);
    }

    @Override
    public InvoiceOutput findByOrderIdAndCustomerId(String orderId, UUID customerId) {
        Invoice invoice = invoiceRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(InvoiceNotFoundException::new);
        return mapper.convert(invoice, InvoiceOutput.class);
    }
}
