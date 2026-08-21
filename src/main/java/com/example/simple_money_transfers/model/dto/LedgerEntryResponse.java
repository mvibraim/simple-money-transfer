package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.example.simple_money_transfers.model.entity.Direction;
import com.example.simple_money_transfers.model.entity.LedgerEntry;

public record LedgerEntryResponse(Long id, UUID transferId, Direction direction,
		@Schema(description = "Signed: negative for DEBIT, positive for CREDIT", example = "-25.00") BigDecimal amount,
		@Schema(description = "ISO 4217 currency code", example = "USD") String currency,
		@Schema(description = "This account's balance immediately after the entry",
				example = "75.00") BigDecimal balanceAfter,
		Instant createdAt) {

	public static LedgerEntryResponse from(LedgerEntry entry) {
		return new LedgerEntryResponse(entry.getId(), entry.getTransferId(), entry.getDirection(), entry.getAmount(),
				entry.getCurrency(), entry.getBalanceAfter(), entry.getCreatedAt());
	}

}
