-- Literal UUIDs, not a DB-generated function: Postgres uses
-- gen_random_uuid(), H2 uses RANDOM_UUID(), and neither name is portable.
-- balance/version/created_at/updated_at are all omitted so their DEFAULT
-- clauses apply (unlike JPA-driven inserts, a plain SQL INSERT that omits
-- a column really does fall back to its DEFAULT).
INSERT INTO account (id, account_ref, holder_name, account_type, currency, status)
VALUES
  ('00000000-0000-0000-0000-000000000001', 'SYSTEM-USD', 'System Account (USD)', 'SYSTEM', 'USD', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000002', 'SYSTEM-EUR', 'System Account (EUR)', 'SYSTEM', 'EUR', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000003', 'SYSTEM-GBP', 'System Account (GBP)', 'SYSTEM', 'GBP', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000004', 'SYSTEM-JPY', 'System Account (JPY)', 'SYSTEM', 'JPY', 'ACTIVE');
