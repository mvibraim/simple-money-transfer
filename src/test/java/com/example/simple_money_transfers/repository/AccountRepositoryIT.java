package com.example.simple_money_transfers.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountStatus;
import com.example.simple_money_transfers.model.entity.AccountType;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;

class AccountRepositoryIT extends AbstractIntegrationTest {

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * The ascending-id lock ordering (F09) is the entire deadlock-avoidance mechanism for
	 * the transfer write path, and it is currently enforced only by code review - nothing
	 * else in the suite would fail if a future edit weakened the lock mode or dropped the
	 * {@code ORDER BY}. This is a red build for exactly that: deleting the ordering, or
	 * downgrading the lock, fails here instead of silently degrading a production-only
	 * guarantee (see {@code docs/features/00-index.md}'s testing-strategy section on what
	 * F15's concurrency tests can and can't prove on H2).
	 */
	@Test
	void lockAllByIdLocksPessimisticallyInAscendingIdOrder() throws NoSuchMethodException {
		Method lockAllById = AccountRepository.class.getMethod("lockAllById", Collection.class);

		Lock lock = lockAllById.getAnnotation(Lock.class);
		assertThat(lock).isNotNull();
		assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);

		Query query = lockAllById.getAnnotation(Query.class);
		assertThat(query).isNotNull();
		assertThat(query.value().strip().toLowerCase()).endsWith("order by a.id");
	}

	@Test
	void savesAndReloadsAnAccountAtZeroBalance() {
		Account account = accountRepository
			.save(new Account("REF-001", "Alice", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getAccountRef()).isEqualTo("REF-001");
		assertThat(reloaded.getHolderName()).isEqualTo("Alice");
		assertThat(reloaded.getCurrency()).isEqualTo("USD");
		assertThat(reloaded.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(reloaded.getVersion()).isEqualTo(0L);
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void databaseRejectsANegativeBalanceOnACustomerAccount() {
		Account account = accountRepository
			.save(new Account("REF-002", "Bob", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		accountRepository.flush();

		assertThatThrownBy(() -> jdbcTemplate.update("UPDATE account SET balance = -1 WHERE id = ?", account.getId()))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void databaseAllowsANegativeBalanceOnASystemAccount() {
		Account account = accountRepository
			.save(new Account("SYS-USD", "System USD", AccountType.SYSTEM, "USD", AccountStatus.ACTIVE));
		accountRepository.flush();

		jdbcTemplate.update("UPDATE account SET balance = -100 WHERE id = ?", account.getId());

		Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getBalance()).isEqualByComparingTo("-100");
	}

	@Test
	void databaseRejectsAnInvalidCurrencyCode() {
		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO account (id, account_ref, holder_name, currency) VALUES (?, 'BAD-1', 'Bad', 'usd')",
				UUID.randomUUID()))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void databaseRejectsAnUnknownAccountType() {
		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO account (id, account_ref, holder_name, account_type, currency) VALUES (?, 'BAD-2', 'Bad', 'BOGUS', 'USD')",
				UUID.randomUUID()))
			.isInstanceOf(DataAccessException.class);
	}

}
