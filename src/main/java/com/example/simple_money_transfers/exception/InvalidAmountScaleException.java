package com.example.simple_money_transfers.exception;

public class InvalidAmountScaleException extends InvalidMoneyException {

	public InvalidAmountScaleException(String currencyCode, int maxScale, int actualScale) {
		super("Amount has %d decimal place(s) but %s allows at most %d".formatted(actualScale, currencyCode, maxScale));
	}

}
