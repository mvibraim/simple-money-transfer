package com.example.simple_money_transfers.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Every integration test shares one H2 in-memory database for the whole
 * test run ({@code DB_CLOSE_DELAY=-1}), so state that survives past the
 * end of a test method would otherwise leak into every test that runs
 * after it. Cleared here, child tables first, so tests can assume a
 * clean slate.
 */
@Tag("integration")
@SpringBootTest
public abstract class AbstractIntegrationTest {

	private static final String[] TABLES_CHILD_TO_PARENT = {
			"ledger_entry", "transfer", "account"
	};

	@Autowired
	private JdbcClient jdbcClient;

	@AfterEach
	void clearDomainTables() {
		// H2 refuses TRUNCATE on any table referenced by a foreign key,
		// even an empty referencing table, so DELETE is used instead -
		// safe here since children are always cleared before parents.
		for (String table : TABLES_CHILD_TO_PARENT) {
			jdbcClient.sql("DELETE FROM " + table).update();
		}
	}

}
