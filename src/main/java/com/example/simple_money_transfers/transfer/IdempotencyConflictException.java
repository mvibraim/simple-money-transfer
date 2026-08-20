package com.example.simple_money_transfers.transfer;

import com.example.simple_money_transfers.error.BusinessRuleException;

public class IdempotencyConflictException extends BusinessRuleException {

	public IdempotencyConflictException(String idempotencyKey) {
		super("Idempotency-Key %s was already used for a different request".formatted(idempotencyKey));
	}

}
