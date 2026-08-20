package com.example.simple_money_transfers.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.simple_money_transfers.account.Account;
import com.example.simple_money_transfers.account.AccountRepository;
import com.example.simple_money_transfers.account.AccountStatus;
import com.example.simple_money_transfers.account.AccountType;
import com.example.simple_money_transfers.error.NotFoundException;
import com.example.simple_money_transfers.money.InvalidMoneyException;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;
import com.example.simple_money_transfers.support.LedgerInvariants;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class TransferServiceIT extends AbstractIntegrationTest {

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransferService transferService;

	@Autowired
	private JdbcClient jdbcClient;

	@AfterEach
	void ledgerInvariantsAlwaysHold() {
		LedgerInvariants.assertAll(jdbcClient);
	}

	/**
	 * Funds a test account through a real deposit (F11's seeded SYSTEM accounts), rather
	 * than setting the balance directly - a direct balance write with no matching ledger
	 * entry would violate the very invariant {@link LedgerInvariants} checks after every
	 * test.
	 */
	private Account fundedAccount(String ref, BigDecimal balance, String currency, AccountStatus status) {
		Account account = accountRepository
			.save(new Account(ref, "Holder " + ref, AccountType.CUSTOMER, currency, AccountStatus.ACTIVE));
		if (balance.signum() > 0) {
			transferService.deposit(account.getId(), balance, null);
		}
		if (status != AccountStatus.ACTIVE) {
			jdbcClient.sql("UPDATE account SET status = ? WHERE id = ?")
				.params(status.name(), account.getId())
				.update();
		}
		return accountRepository.findById(account.getId()).orElseThrow();
	}

	@Test
	void happyPathMovesBothBalancesAndPostsABalancedLedgerPair() {
		Account source = fundedAccount("S1", new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE);
		Account target = fundedAccount("T1", new BigDecimal("0.00"), "USD", AccountStatus.ACTIVE);

		Transfer transfer = transferService.execute(new TransferCommand(source.getId(), target.getId(),
				new BigDecimal("30.00"), "USD", TransferKind.TRANSFER, "test"));

		assertThat(transfer.getId()).isNotNull();
		assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("70.0000");
		assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("30.0000");
	}

	@Test
	void insufficientFundsLeavesBalancesAndLedgerUnchanged() {
		Account source = fundedAccount("S2", new BigDecimal("10.00"), "USD", AccountStatus.ACTIVE);
		Account target = fundedAccount("T2", new BigDecimal("0.00"), "USD", AccountStatus.ACTIVE);

		assertThatThrownBy(() -> transferService.execute(new TransferCommand(source.getId(), target.getId(),
				new BigDecimal("30.00"), "USD", TransferKind.TRANSFER, null)))
			.isInstanceOf(InsufficientFundsException.class);

		assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("10.0000");
		assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("0.0000");
	}

	@Test
	void currencyMismatchIsRejected() {
		Account source = fundedAccount("S3", new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE);
		Account target = fundedAccount("T3", new BigDecimal("0.00"), "EUR", AccountStatus.ACTIVE);

		assertThatThrownBy(() -> transferService.execute(new TransferCommand(source.getId(), target.getId(),
				new BigDecimal("10.00"), "USD", TransferKind.TRANSFER, null)))
			.isInstanceOf(CurrencyMismatchException.class);
	}

	@Test
	void selfTransferIsRejected() {
		Account account = fundedAccount("S4", new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE);

		assertThatThrownBy(() -> transferService.execute(new TransferCommand(account.getId(), account.getId(),
				new BigDecimal("10.00"), "USD", TransferKind.TRANSFER, null)))
			.isInstanceOf(SelfTransferException.class);
	}

	@Test
	void inactiveSourceAccountIsRejected() {
		Account source = fundedAccount("S5", new BigDecimal("100.00"), "USD", AccountStatus.FROZEN);
		Account target = fundedAccount("T5", new BigDecimal("0.00"), "USD", AccountStatus.ACTIVE);

		assertThatThrownBy(() -> transferService.execute(new TransferCommand(source.getId(), target.getId(),
				new BigDecimal("10.00"), "USD", TransferKind.TRANSFER, null)))
			.isInstanceOf(InactiveAccountException.class);
	}

	@Test
	void inactiveTargetAccountIsRejected() {
		Account source = fundedAccount("S6", new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE);
		Account target = fundedAccount("T6", new BigDecimal("0.00"), "USD", AccountStatus.CLOSED);

		assertThatThrownBy(() -> transferService.execute(new TransferCommand(source.getId(), target.getId(),
				new BigDecimal("10.00"), "USD", TransferKind.TRANSFER, null)))
			.isInstanceOf(InactiveAccountException.class);
	}

	@Test
	void nonPositiveAmountIsRejected() {
		Account source = fundedAccount("S7", new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE);
		Account target = fundedAccount("T7", new BigDecimal("0.00"), "USD", AccountStatus.ACTIVE);

		assertThatThrownBy(() -> transferService.execute(new TransferCommand(source.getId(), target.getId(),
				new BigDecimal("0.00"), "USD", TransferKind.TRANSFER, null)))
			.isInstanceOf(InvalidMoneyException.class);
	}

	@Test
	void unknownAccountIsRejected() {
		Account source = fundedAccount("S8", new BigDecimal("100.00"), "USD", AccountStatus.ACTIVE);

		assertThatThrownBy(() -> transferService.execute(new TransferCommand(source.getId(), UUID.randomUUID(),
				new BigDecimal("10.00"), "USD", TransferKind.TRANSFER, null)))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void systemAccountSourceIsExemptFromInsufficientFunds() {
		// CHF deliberately: this test drives execute() directly with an
		// explicit account id, not through deposit()'s currency lookup, but
		// a second SYSTEM/USD row would still collide with F11's seeded one
		// for any other test relying on that lookup.
		Account system = accountRepository
			.save(new Account("SYS1", "System", AccountType.SYSTEM, "CHF", AccountStatus.ACTIVE));
		Account customer = fundedAccount("C1", new BigDecimal("0.00"), "CHF", AccountStatus.ACTIVE);

		Transfer deposit = transferService.execute(new TransferCommand(system.getId(), customer.getId(),
				new BigDecimal("50.00"), "CHF", TransferKind.DEPOSIT, null));

		assertThat(deposit.getId()).isNotNull();
		assertThat(accountRepository.findById(system.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("-50.0000");
		assertThat(accountRepository.findById(customer.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("50.0000");
	}

}
