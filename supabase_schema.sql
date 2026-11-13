create table if not exists public.transactions (
  transaction_id text primary key,
  account_id text not null,
  date_time timestamp without time zone not null,
  type text not null check (type in ('CREDIT', 'DEBIT')),
  amount numeric(14,2) not null check (amount > 0),
  mode text not null,
  description text not null
);

alter table public.transactions enable row level security;

insert into public.transactions
  (transaction_id, account_id, date_time, type, amount, mode, description)
values
  ('T1001', 'ACC100', '2026-11-10 10:30:00', 'DEBIT', 450.00, 'UPI', 'Grocery payment'),
  ('T1002', 'ACC100', '2026-11-12 14:15:00', 'CREDIT', 5000.00, 'NET_BANKING', 'Demo salary credit'),
  ('T1003', 'ACC100', '2026-11-13 16:00:00', 'DEBIT', 250.00, 'UPI', 'Demo transfer')
on conflict (transaction_id) do nothing;