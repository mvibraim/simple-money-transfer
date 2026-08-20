package com.example.simple_money_transfers.transfer;

import com.example.simple_money_transfers.error.BusinessRuleException;

public class SelfTransferException extends BusinessRuleException {

	public SelfTransferException() {
		super("Source and target account must be different");
	}

}
