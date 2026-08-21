package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.example.simple_money_transfers.model.entity.Account;

public record BalanceResponse(UUID accountId, @Schema(example = "100.00") BigDecimal balance,
		@Schema(description = "ISO 4217 currency code", example = "USD") String currency, Instant asOf) {

	public static BalanceResponse from(Account account) {
		return new BalanceResponse(account.getId(), account.getBalance(), account.getCurrency(), Instant.now());
	}

}
