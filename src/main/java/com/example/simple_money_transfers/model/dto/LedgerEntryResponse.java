package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.simple_money_transfers.model.entity.Direction;
import com.example.simple_money_transfers.model.entity.LedgerEntry;

public record LedgerEntryResponse(Long id, UUID transferId, Direction direction, BigDecimal amount, String currency,
		BigDecimal balanceAfter, Instant createdAt) {

	public static LedgerEntryResponse from(LedgerEntry entry) {
		return new LedgerEntryResponse(entry.getId(), entry.getTransferId(), entry.getDirection(), entry.getAmount(),
				entry.getCurrency(), entry.getBalanceAfter(), entry.getCreatedAt());
	}

}
