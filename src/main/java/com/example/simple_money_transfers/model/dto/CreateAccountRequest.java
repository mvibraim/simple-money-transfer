package com.example.simple_money_transfers.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(@Schema(example = "Alice") @NotBlank String holderName,
		@Schema(description = "ISO 4217 currency code",
				example = "USD") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
