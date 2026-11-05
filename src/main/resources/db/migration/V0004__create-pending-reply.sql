-- reply tardios - como se fosse um outbox
create table public.pending_reply (
    invoice_id     uuid                     not null,
    order_id       varchar(255)             not null,
    correlation_id varchar(255)             not null,
    reply_topic    varchar(255)             not null,
    created_at     timestamp with time zone not null,
    primary key (invoice_id),
    constraint fk_pending_reply_invoice_id foreign key (invoice_id) references public.invoice (id)
);