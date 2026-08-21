package com.example.simple_money_transfers.exception;

import java.util.UUID;

public class InsufficientFundsException extends BusinessRuleException {

	public InsufficientFundsException(UUID accountId) {
		super("Account %s has insufficient funds".formatted(accountId));
	}

}
