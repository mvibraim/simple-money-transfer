package com.example.simple_money_transfers.model.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WithdrawalRequest(@Schema(example = "50.00") @NotNull @Positive BigDecimal amount,
		@Schema(example = "rent") @Size(max = 140) String reference) {
}
