package com.example.simple_money_transfers.transfer;

import java.util.UUID;

import com.example.simple_money_transfers.error.BusinessRuleException;

public class InsufficientFundsException extends BusinessRuleException {

	public InsufficientFundsException(UUID accountId) {
		super("Account %s has insufficient funds".formatted(accountId));
	}

}
