package com.example.simple_money_transfers.transfer;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String currency,
		TransferKind kind, String reference) {
}
