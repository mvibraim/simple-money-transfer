package com.example.simple_money_transfers.money;

public class InvalidMoneyException extends RuntimeException {

	public InvalidMoneyException(String message) {
		super(message);
	}

}
