-- Postgres-only: H2 has no PL/pgSQL, so this cannot be exercised by the
-- test suite. It is defence in depth against raw SQL, a future ad-hoc
-- script, or a compromised dependency rewriting ledger history - the
-- application-level guard (LedgerEntryRepository exposing no mutating
-- method) is what the H2-backed tests actually verify.
CREATE OR REPLACE FUNCTION prevent_ledger_entry_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entry rows are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entry_immutable
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_entry_mutation();
