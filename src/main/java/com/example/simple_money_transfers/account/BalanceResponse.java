package com.example.simple_money_transfers.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(UUID accountId, BigDecimal balance, String currency, Instant asOf) {

	static BalanceResponse from(Account account) {
		return new BalanceResponse(account.getId(), account.getBalance(), account.getCurrency(), Instant.now());
	}
}
