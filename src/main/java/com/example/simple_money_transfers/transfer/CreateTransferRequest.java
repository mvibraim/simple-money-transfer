package com.example.simple_money_transfers.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID targetAccountId,
		@NotNull @Positive BigDecimal amount, @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
		@Size(max = 140) String reference) {
}
