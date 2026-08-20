package com.example.simple_money_transfers.transfer;

import com.example.simple_money_transfers.error.BusinessRuleException;

public class UnsupportedCurrencyException extends BusinessRuleException {

	public UnsupportedCurrencyException(String currency) {
		super("No system account is configured for currency %s".formatted(currency));
	}

}
