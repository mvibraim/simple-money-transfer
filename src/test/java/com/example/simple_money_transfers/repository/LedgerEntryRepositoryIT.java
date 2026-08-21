package com.example.simple_money_transfers.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountStatus;
import com.example.simple_money_transfers.model.entity.AccountType;
import com.example.simple_money_transfers.model.entity.Direction;
import com.example.simple_money_transfers.model.entity.LedgerEntry;
import com.example.simple_money_transfers.model.entity.Transfer;
import com.example.simple_money_transfers.model.entity.TransferKind;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;

class LedgerEntryRepositoryIT extends AbstractIntegrationTest {

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransferRepository transferRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void repositoryExposesNoUpdateOrDeleteMethod() {
		var methodNames = Arrays.stream(LedgerEntryRepository.class.getMethods()).map(Method::getName).toList();
		assertThat(methodNames).containsExactlyInAnyOrder("save", "findById", "findByAccountId");
	}

	@Test
	void databaseRejectsASecondEntryForTheSameTransferAndAccount() {
		Account source = accountRepository
			.save(new Account("REF-S", "Source", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		Account target = accountRepository
			.save(new Account("REF-T", "Target", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		Transfer transfer = transferRepository.save(new Transfer(source.getId(), target.getId(),
				new BigDecimal("10.0000"), "USD", TransferKind.TRANSFER, null));
		accountRepository.flush();

		ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), source.getId(), Direction.DEBIT,
				new BigDecimal("-10.0000"), "USD", new BigDecimal("-10.0000")));

		assertThatThrownBy(() -> ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), source.getId(),
				Direction.DEBIT, new BigDecimal("-5.0000"), "USD", new BigDecimal("-15.0000"))))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void databaseRejectsADebitRecordedAsAPositiveAmount() {
		Account source = accountRepository
			.save(new Account("REF-S2", "Source", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		Account target = accountRepository
			.save(new Account("REF-T2", "Target", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		Transfer transfer = transferRepository.save(new Transfer(source.getId(), target.getId(),
				new BigDecimal("10.0000"), "USD", TransferKind.TRANSFER, null));
		accountRepository.flush();

		assertThatThrownBy(() -> jdbcTemplate
			.update("INSERT INTO ledger_entry (transfer_id, account_id, direction, amount, currency, balance_after) "
					+ "VALUES (?, ?, 'DEBIT', 10.0000, 'USD', 10.0000)", transfer.getId(), source.getId()))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void savesAndReloadsALedgerEntry() {
		Account source = accountRepository
			.save(new Account("REF-S3", "Source", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		Account target = accountRepository
			.save(new Account("REF-T3", "Target", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		Transfer transfer = transferRepository.save(new Transfer(source.getId(), target.getId(),
				new BigDecimal("10.0000"), "USD", TransferKind.TRANSFER, null));

		LedgerEntry saved = ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), target.getId(),
				Direction.CREDIT, new BigDecimal("10.0000"), "USD", new BigDecimal("10.0000")));

		LedgerEntry reloaded = ledgerEntryRepository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getDirection()).isEqualTo(Direction.CREDIT);
		assertThat(reloaded.getAmount()).isEqualByComparingTo("10.0000");
		assertThat(reloaded.getBalanceAfter()).isEqualByComparingTo("10.0000");
		assertThat(reloaded.getCreatedAt()).isNotNull();
	}

	@Test
	void transferRepositoryExposesNoUpdateOrDeleteMethod() {
		var methodNames = Arrays.stream(TransferRepository.class.getMethods()).map(Method::getName).toList();
		assertThat(methodNames).containsExactlyInAnyOrder("save", "findById");
	}

}
