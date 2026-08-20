package com.example.simple_money_transfers.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(@NotBlank String holderName,
		@NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
