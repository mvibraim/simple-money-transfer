package com.example.simple_money_transfers.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DepositRequest(
    @NotNull @Positive BigDecimal amount, @Size(max = 140) String reference) {}
