package com.example.simple_money_transfers.transfer;

import java.util.UUID;

import com.example.simple_money_transfers.error.BusinessRuleException;

public class InactiveAccountException extends BusinessRuleException {

	public InactiveAccountException(UUID accountId) {
		super("Account %s is not active".formatted(accountId));
	}

}
