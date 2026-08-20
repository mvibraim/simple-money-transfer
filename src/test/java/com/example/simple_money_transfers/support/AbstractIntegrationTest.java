package com.example.simple_money_transfers.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Every integration test shares one H2 in-memory database for the whole test run ({@code
 * DB_CLOSE_DELAY=-1}), so state that survives past the end of a test method would otherwise leak
 * into every test that runs after it. Cleared here, child tables first, so tests can assume a clean
 * slate - including F11's migration-seeded {@code SYSTEM} accounts, which are deleted along with
 * everything else and then re-seeded, rather than special-cased and preserved. Re-seeding is
 * simpler to reason about globally than requiring every test that creates its own ad-hoc {@code
 * SYSTEM} account to avoid colliding with the seeded ones by currency - that was tried first and
 * turned into exactly the kind of cross-test fragility this class exists to prevent.
 */
@Tag("integration")
@SpringBootTest
public abstract class AbstractIntegrationTest {

  @Autowired private JdbcClient jdbcClient;

  @AfterEach
  void clearDomainTables() {
    // H2 refuses TRUNCATE on any table referenced by a foreign key,
    // even an empty referencing table, so DELETE is used instead -
    // safe here since children are always cleared before parents.
    jdbcClient.sql("DELETE FROM idempotency_record").update();
    jdbcClient.sql("DELETE FROM ledger_entry").update();
    jdbcClient.sql("DELETE FROM transfer").update();
    jdbcClient.sql("DELETE FROM account").update();
    reseedSystemAccounts();
  }

  private void reseedSystemAccounts() {
    // Mirrors V4__system_accounts.sql exactly - kept here rather than
    // re-reading it, since re-running Flyway per test would be far
    // slower than one INSERT.
    jdbcClient
        .sql(
            """
				INSERT INTO account (id, account_ref, holder_name, account_type, currency, status)
				VALUES
				  ('00000000-0000-0000-0000-000000000001', 'SYSTEM-USD', 'System Account (USD)', 'SYSTEM', 'USD', 'ACTIVE'),
				  ('00000000-0000-0000-0000-000000000002', 'SYSTEM-EUR', 'System Account (EUR)', 'SYSTEM', 'EUR', 'ACTIVE'),
				  ('00000000-0000-0000-0000-000000000003', 'SYSTEM-GBP', 'System Account (GBP)', 'SYSTEM', 'GBP', 'ACTIVE'),
				  ('00000000-0000-0000-0000-000000000004', 'SYSTEM-JPY', 'System Account (JPY)', 'SYSTEM', 'JPY', 'ACTIVE')
				""")
        .update();
  }
}
