package com.example.simple_money_transfers.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The invariants that must hold after every write, called from every integration test's
 * {@code @AfterEach} from F09 onward - which makes each functional test a partial
 * concurrency test for free.
 */
public final class LedgerInvariants {

	private LedgerInvariants() {
	}

	public static void assertAll(JdbcClient jdbcClient) {
		assertLedgerSumsToZero(jdbcClient);
		assertEveryBalanceMatchesItsLedgerEntries(jdbcClient);
		assertNoNegativeCustomerBalance(jdbcClient);
		assertExactlyOneDebitAndOneCreditPerTransfer(jdbcClient);
	}

	private static void assertLedgerSumsToZero(JdbcClient jdbcClient) {
		BigDecimal sum = jdbcClient.sql("SELECT COALESCE(SUM(amount), 0) FROM ledger_entry")
			.query(BigDecimal.class)
			.single();
		assertThat(sum).as("SUM(ledger_entry.amount) must be exactly zero").isEqualByComparingTo(BigDecimal.ZERO);
	}

	private static void assertEveryBalanceMatchesItsLedgerEntries(JdbcClient jdbcClient) {
		var mismatches = jdbcClient.sql("""
				SELECT a.id, a.account_ref, a.account_type, a.balance, COALESCE(SUM(le.amount), 0) AS ledger_sum
				FROM account a
				LEFT JOIN ledger_entry le ON le.account_id = a.id
				GROUP BY a.id, a.account_ref, a.account_type, a.balance
				HAVING a.balance <> COALESCE(SUM(le.amount), 0)
				""").query().listOfRows();
		assertThat(mismatches).as("every account balance must equal the sum of its own ledger entries").isEmpty();
	}

	private static void assertNoNegativeCustomerBalance(JdbcClient jdbcClient) {
		var negative = jdbcClient.sql("SELECT id FROM account WHERE account_type = 'CUSTOMER' AND balance < 0")
			.query()
			.listOfRows();
		assertThat(negative).as("no customer account may have a negative balance").isEmpty();
	}

	private static void assertExactlyOneDebitAndOneCreditPerTransfer(JdbcClient jdbcClient) {
		var malformed = jdbcClient.sql("""
				SELECT transfer_id
				FROM ledger_entry
				GROUP BY transfer_id
				HAVING SUM(CASE WHEN direction = 'DEBIT' THEN 1 ELSE 0 END) <> 1
				    OR SUM(CASE WHEN direction = 'CREDIT' THEN 1 ELSE 0 END) <> 1
				""").query().listOfRows();
		assertThat(malformed).as("every transfer must have exactly one debit and one credit entry").isEmpty();
	}

}
