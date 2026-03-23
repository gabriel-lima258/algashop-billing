create table public.credit_card (
	id uuid not null,
	created_at timestamp with time zone,
	customer_id uuid,
	last_numbers varchar(255),
	brand varchar(255),
	exp_month integer,
	exp_year integer,
	gateway_code varchar(255),
	primary key (id)
);

create table public.payment_settings (
	id uuid not null,
	credit_card_id uuid,
	gateway_code varchar(255),
	payment_method varchar(255),
	primary key (id)
);

create table public.invoice (
	id uuid not null,
	order_id varchar(255),
	customer_id uuid,
	issued_at timestamp with time zone,
	paid_at timestamp with time zone,
	canceled_at timestamp with time zone,
	expires_at timestamp with time zone,
	total_amount numeric(38,2),
	status varchar(255),
	cancel_reason varchar(255),
	payment_settings_id uuid,
	payer_full_name varchar(255),
	payer_document varchar(255),
	payer_phone varchar(255),
	payer_email varchar(255),
	payer_address_street varchar(255),
	payer_address_number varchar(255),
	payer_address_complement varchar(255),
	payer_address_neighborhood varchar(255),
	payer_address_city varchar(255),
	payer_address_state varchar(255),
	payer_address_zip_code varchar(255),
	created_by_user_id uuid,
	created_at timestamp with time zone,
	last_modified_by_user_id uuid,
	last_modified_date timestamp with time zone,
	version bigint,
	constraint uq_invoice_order_id unique (order_id),
	primary key (id)
);

alter table public.invoice add constraint fk_invoice_payment_settings_id foreign key (payment_settings_id) references public.payment_settings(id);

create table public.invoice_line_item (
	invoice_id uuid not null,
	number integer,
	name varchar(255),
	amount numeric(38,2),
	constraint fk_invoice_line_item_invoice_id foreign key (invoice_id) references public.invoice(id)
);

create index idx_invoice_line_item_invoice_id on public.invoice_line_item (invoice_id);
