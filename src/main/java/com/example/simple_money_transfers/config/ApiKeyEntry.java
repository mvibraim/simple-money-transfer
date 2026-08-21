package com.example.simple_money_transfers.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyEntry(@NotBlank String id, @NotBlank @Size(min = 32) String key) {

	@Override
	public String toString() {
		return "ApiKeyEntry[id=%s, key=REDACTED]".formatted(id);
	}

}
