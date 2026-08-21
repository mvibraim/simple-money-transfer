package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.simple_money_transfers.model.entity.Account;

public record BalanceResponse(UUID accountId, BigDecimal balance, String currency, Instant asOf) {

	public static BalanceResponse from(Account account) {
		return new BalanceResponse(account.getId(), account.getBalance(), account.getCurrency(), Instant.now());
	}

}
