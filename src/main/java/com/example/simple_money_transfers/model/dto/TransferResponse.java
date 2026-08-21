package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.example.simple_money_transfers.model.entity.Transfer;
import com.example.simple_money_transfers.model.entity.TransferKind;

public record TransferResponse(UUID id,
		@Schema(description = "For a DEPOSIT, the funding SYSTEM account") UUID sourceAccountId,
		@Schema(description = "For a WITHDRAWAL, the receiving SYSTEM account") UUID targetAccountId,
		@Schema(example = "25.00") BigDecimal amount,
		@Schema(description = "ISO 4217 currency code", example = "USD") String currency, TransferKind kind,
		String reference, Instant createdAt) {

	public static TransferResponse from(Transfer transfer) {
		return new TransferResponse(transfer.getId(), transfer.getSourceAccountId(), transfer.getTargetAccountId(),
				transfer.getAmount(), transfer.getCurrency(), transfer.getKind(), transfer.getReference(),
				transfer.getCreatedAt());
	}

}
