package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountStatus;
import com.example.simple_money_transfers.model.entity.AccountType;

public record AccountResponse(UUID id,
		@Schema(description = "System-generated reference, distinct from the id",
				example = "ACCT-7F3A2B1CQ9ZK") String accountRef,
		String holderName, AccountType accountType,
		@Schema(description = "ISO 4217 currency code", example = "USD") String currency,
		@Schema(example = "100.00") BigDecimal balance, AccountStatus status, Instant createdAt) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(account.getId(), account.getAccountRef(), account.getHolderName(),
				account.getAccountType(), account.getCurrency(), account.getBalance(), account.getStatus(),
				account.getCreatedAt());
	}

}
