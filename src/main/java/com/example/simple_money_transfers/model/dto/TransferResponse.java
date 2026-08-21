package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.simple_money_transfers.model.entity.Transfer;
import com.example.simple_money_transfers.model.entity.TransferKind;

public record TransferResponse(UUID id, UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String currency,
		TransferKind kind, String reference, Instant createdAt) {

	public static TransferResponse from(Transfer transfer) {
		return new TransferResponse(transfer.getId(), transfer.getSourceAccountId(), transfer.getTargetAccountId(),
				transfer.getAmount(), transfer.getCurrency(), transfer.getKind(), transfer.getReference(),
				transfer.getCreatedAt());
	}

}
