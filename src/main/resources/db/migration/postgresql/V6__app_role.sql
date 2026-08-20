-- Postgres-only, defence in depth for F08's ledger immutability at the
-- privilege level: the running application connects as this restricted
-- role (spring.datasource.*) rather than the migration owner
-- (spring.flyway.user/password), so even a future ad-hoc script or a
-- compromised dependency running as the application cannot rewrite
-- ledger history via raw SQL - only the F08 trigger stood in the way
-- before this. Extended to transfer and idempotency_record too: both
-- are append-only audit records by the same design as ledger_entry
-- (F08, F13), so the same restriction applies for the same reason.
--
-- ${app_password} is a Flyway placeholder (spring.flyway.placeholders),
-- not a hardcoded secret - never commit a real password in a migration.
CREATE ROLE money_app LOGIN PASSWORD '${app_password}';

GRANT CONNECT ON DATABASE money TO money_app;
GRANT USAGE ON SCHEMA public TO money_app;

GRANT SELECT, INSERT, UPDATE ON account TO money_app;
GRANT SELECT, INSERT ON transfer, ledger_entry, idempotency_record TO money_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO money_app;
