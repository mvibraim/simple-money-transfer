package com.example.simple_money_transfers.transfer;

import com.example.simple_money_transfers.error.BusinessRuleException;
import java.util.UUID;

public class InsufficientFundsException extends BusinessRuleException {

  public InsufficientFundsException(UUID accountId) {
    super("Account %s has insufficient funds".formatted(accountId));
  }
}
