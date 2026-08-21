package com.example.simple_money_transfers.transfer;

import com.example.simple_money_transfers.account.Account;
import com.example.simple_money_transfers.account.AccountRepository;
import com.example.simple_money_transfers.account.AccountStatus;
import com.example.simple_money_transfers.account.AccountType;
import com.example.simple_money_transfers.error.NotFoundException;
import com.example.simple_money_transfers.ledger.Direction;
import com.example.simple_money_transfers.ledger.LedgerEntry;
import com.example.simple_money_transfers.ledger.LedgerEntryRepository;
import com.example.simple_money_transfers.money.InvalidMoneyException;
import com.example.simple_money_transfers.money.MoneyNormalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place in the codebase that ever mutates an account balance or writes a
 * ledger entry. See the F09 design doc for why locking is ordered and pessimistic - the
 * short version: {@link AccountRepository#lockAllById} acquires both accounts in one
 * query, ascending by id, which is what makes two opposing concurrent transfers resolve
 * to a wait instead of a deadlock. That guarantee holds only as long as no other code
 * path locks an account outside that one query.
 */
@Service
public class TransferService {

	private final AccountRepository accountRepository;

	private final TransferRepository transferRepository;

	private final LedgerEntryRepository ledgerEntryRepository;

	// Self-injected proxy: deposit()/withdraw() call execute() through this
	// instead of via `this`, so the call crosses Spring's transactional
	// proxy instead of bypassing it - see the Sonar-flagged self-invocation
	// pitfall this fixes.
	private final TransferService self;

	public TransferService(AccountRepository accountRepository, TransferRepository transferRepository,
			LedgerEntryRepository ledgerEntryRepository, @Lazy TransferService self) {
		this.accountRepository = accountRepository;
		this.transferRepository = transferRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.self = self;
	}

	@Transactional
	public Transfer execute(TransferCommand command) {
		if (command.sourceAccountId().equals(command.targetAccountId())) {
			throw new SelfTransferException();
		}
		if (command.amount() == null || command.amount().signum() <= 0) {
			throw new InvalidMoneyException("Amount must be positive");
		}
		BigDecimal amount = MoneyNormalizer.normalize(command.amount(), command.currency());

		List<Account> accounts = accountRepository
			.lockAllById(List.of(command.sourceAccountId(), command.targetAccountId()));
		if (accounts.size() != 2) {
			throw new NotFoundException("Source and/or target account not found");
		}
		Account source = accountById(accounts, command.sourceAccountId());
		Account target = accountById(accounts, command.targetAccountId());

		if (source.getStatus() != AccountStatus.ACTIVE) {
			throw new InactiveAccountException(source.getId());
		}
		if (target.getStatus() != AccountStatus.ACTIVE) {
			throw new InactiveAccountException(target.getId());
		}
		if (!source.getCurrency().equals(command.currency()) || !target.getCurrency().equals(command.currency())) {
			throw new CurrencyMismatchException(command.currency(), source.getCurrency(), target.getCurrency());
		}
		// SYSTEM accounts are the exempt-from-overdraft boundary where
		// money enters and leaves the system (F11); only CUSTOMER
		// accounts are subject to the insufficient-funds business rule.
		if (source.getAccountType() == AccountType.CUSTOMER && source.getBalance().compareTo(amount) < 0) {
			throw new InsufficientFundsException(source.getId());
		}

		BigDecimal sourceBalanceAfter = source.getBalance().subtract(amount);
		BigDecimal targetBalanceAfter = target.getBalance().add(amount);
		source.setBalance(sourceBalanceAfter);
		target.setBalance(targetBalanceAfter);

		Transfer transfer = transferRepository.save(new Transfer(source.getId(), target.getId(), amount,
				command.currency(), command.kind(), command.reference()));

		ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), source.getId(), Direction.DEBIT, amount.negate(),
				command.currency(), sourceBalanceAfter));
		ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), target.getId(), Direction.CREDIT, amount,
				command.currency(), targetBalanceAfter));

		return transfer;
	}

	@Transactional(readOnly = true)
	public Transfer getTransfer(UUID id) {
		return transferRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Transfer %s not found".formatted(id)));
	}

	/**
	 * Always in the account's own currency - there is no FX conversion, so asking the
	 * caller to restate the currency would only invite a spurious mismatch. Reuses
	 * {@link #execute}, so this funding path gets every invariant (locking, atomicity,
	 * the balanced ledger) that the core transfer already established, for free.
	 */
	@Transactional
	public Transfer deposit(UUID accountId, BigDecimal amount, String reference) {
		Account target = requireAccount(accountId);
		Account system = systemAccountFor(target.getCurrency());
		return self.execute(new TransferCommand(system.getId(), target.getId(), amount, target.getCurrency(),
				TransferKind.DEPOSIT, reference));
	}

	@Transactional
	public Transfer withdraw(UUID accountId, BigDecimal amount, String reference) {
		Account source = requireAccount(accountId);
		Account system = systemAccountFor(source.getCurrency());
		return self.execute(new TransferCommand(source.getId(), system.getId(), amount, source.getCurrency(),
				TransferKind.WITHDRAWAL, reference));
	}

	private Account requireAccount(UUID id) {
		return accountRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Account %s not found".formatted(id)));
	}

	private Account systemAccountFor(String currency) {
		return accountRepository.findByAccountTypeAndCurrency(AccountType.SYSTEM, currency)
			.orElseThrow(() -> new UnsupportedCurrencyException(currency));
	}

	private static Account accountById(List<Account> accounts, UUID id) {
		return accounts.stream()
			.filter(account -> account.getId().equals(id))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Account %s not found".formatted(id)));
	}

}
