package com.example.simple_money_transfers.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@code ddl-auto: validate} (F01) checks tables, columns, and types - it does <b>not</b>
 * check CHECK constraints, foreign keys, or unique indexes. A future migration that
 * accidentally dropped {@code account_no_overdraft} or {@code idempotency_key_unique}
 * would start the application cleanly, with no signal that a real invariant is gone. This
 * is the one test in the whole suite that would catch that - and because it reads
 * {@code INFORMATION_SCHEMA} (SQL-standard) rather than a Postgres system catalog, it
 * runs on H2 as part of the everyday suite rather than being another Postgres-only,
 * manually-checked guarantee.
 */
class ConstraintPresenceIT extends AbstractIntegrationTest {

	private static final List<String> EXPECTED_CONSTRAINTS = List.of(
			// F06 - account
			"account_currency_iso", "account_type_valid", "account_status_valid", "account_no_overdraft",
			// F08 - transfer and ledger_entry
			"transfer_amount_positive", "transfer_distinct_accounts", "transfer_kind_valid", "ledger_direction_valid",
			"ledger_sign_matches", "ledger_one_entry_per_account",
			// F13 - idempotency_record
			"idempotency_key_unique");

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void everyNamedConstraintFromTheMigrationsStillExists() {
		Set<String> present = jdbcClient.sql("SELECT constraint_name FROM information_schema.table_constraints")
			.query(String.class)
			.list()
			.stream()
			.map(String::toUpperCase)
			.collect(Collectors.toSet());

		for (String expected : EXPECTED_CONSTRAINTS) {
			assertThat(present).as("constraint '%s' must still exist", expected).contains(expected.toUpperCase());
		}
	}

}
