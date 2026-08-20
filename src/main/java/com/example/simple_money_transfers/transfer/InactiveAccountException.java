package com.example.simple_money_transfers.transfer;

import com.example.simple_money_transfers.error.BusinessRuleException;
import java.util.UUID;

public class InactiveAccountException extends BusinessRuleException {

  public InactiveAccountException(UUID accountId) {
    super("Account %s is not active".formatted(accountId));
  }
}
