alter table public.invoice
    add constraint uk_invoice_order_id unique (order_id);