package com.example.simple_money_transfers.exception;

public class SelfTransferException extends BusinessRuleException {

	public SelfTransferException() {
		super("Source and target account must be different");
	}

}
