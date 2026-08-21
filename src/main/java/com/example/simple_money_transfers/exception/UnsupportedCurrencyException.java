package com.example.simple_money_transfers.exception;

public class UnsupportedCurrencyException extends BusinessRuleException {

	public UnsupportedCurrencyException(String currency) {
		super("No system account is configured for currency %s".formatted(currency));
	}

}
