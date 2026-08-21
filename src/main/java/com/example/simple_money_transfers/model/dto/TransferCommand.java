package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.simple_money_transfers.model.entity.TransferKind;

public record TransferCommand(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String currency,
		TransferKind kind, String reference) {
}
