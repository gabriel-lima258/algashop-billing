package com.algaworks.algashop.billing.application.invoice.command;

import com.algaworks.algashop.billing.application.InboundIntegrationCommand;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancelInvoiceIntegrationCommand implements InboundIntegrationCommand {

    @NotBlank
    private String orderId;
}