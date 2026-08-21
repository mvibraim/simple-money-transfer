package com.example.simple_money_transfers.exception;

import java.util.UUID;

public class InactiveAccountException extends BusinessRuleException {

	public InactiveAccountException(UUID accountId) {
		super("Account %s is not active".formatted(accountId));
	}

}
