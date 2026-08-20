package com.example.simple_money_transfers.money;

public class InvalidCurrencyException extends InvalidMoneyException {

	public InvalidCurrencyException(String currencyCode) {
		super("Not a valid ISO-4217 currency code: " + currencyCode);
	}

}
