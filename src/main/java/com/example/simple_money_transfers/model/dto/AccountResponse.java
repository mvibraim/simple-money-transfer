package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountStatus;
import com.example.simple_money_transfers.model.entity.AccountType;

public record AccountResponse(UUID id, String accountRef, String holderName, AccountType accountType, String currency,
		BigDecimal balance, AccountStatus status, Instant createdAt) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(account.getId(), account.getAccountRef(), account.getHolderName(),
				account.getAccountType(), account.getCurrency(), account.getBalance(), account.getStatus(),
				account.getCreatedAt());
	}

}
