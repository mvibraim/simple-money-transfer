package com.example.simple_money_transfers.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String accountRef, String holderName, AccountType accountType, String currency,
		BigDecimal balance, AccountStatus status, Instant createdAt) {

	static AccountResponse from(Account account) {
		return new AccountResponse(account.getId(), account.getAccountRef(), account.getHolderName(),
				account.getAccountType(), account.getCurrency(), account.getBalance(), account.getStatus(),
				account.getCreatedAt());
	}

}
