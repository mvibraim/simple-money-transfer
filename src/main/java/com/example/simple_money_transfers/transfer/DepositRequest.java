package com.example.simple_money_transfers.transfer;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DepositRequest(@NotNull @Positive BigDecimal amount, @Size(max = 140) String reference) {
}
