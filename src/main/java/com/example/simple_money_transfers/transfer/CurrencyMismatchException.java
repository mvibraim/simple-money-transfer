package com.example.simple_money_transfers.transfer;

import com.example.simple_money_transfers.error.BusinessRuleException;

public class CurrencyMismatchException extends BusinessRuleException {

	public CurrencyMismatchException(String requested, String sourceCurrency, String targetCurrency) {
		super("Requested currency %s does not match source (%s) and/or target (%s) account currency"
				.formatted(requested, sourceCurrency, targetCurrency));
	}

}
