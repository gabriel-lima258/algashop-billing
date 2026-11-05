package com.algaworks.algashop.billing.application;

// Marcador dos comandos que o billing RECEBE do orquestrador (hoje so o IssueInvoice).
// Espelha o OutboundIntegrationCommand do ordering; nao precisa de key: quem a escolheu
// foi o remetente, e ela chega pronta no header RECEIVED_KEY.
public interface InboundIntegrationCommand {
}
