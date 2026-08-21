package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID targetAccountId,
		@Schema(example = "25.00") @NotNull @Positive BigDecimal amount,
		@Schema(description = "ISO 4217 currency code; must match both accounts' currency",
				example = "USD") @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
		@Schema(example = "rent") @Size(max = 140) String reference) {
}
