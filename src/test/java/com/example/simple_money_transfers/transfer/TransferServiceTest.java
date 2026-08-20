package com.example.simple_money_transfers.transfer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.simple_money_transfers.account.Account;
import com.example.simple_money_transfers.account.AccountRepository;
import com.example.simple_money_transfers.account.AccountStatus;
import com.example.simple_money_transfers.account.AccountType;
import com.example.simple_money_transfers.error.NotFoundException;
import com.example.simple_money_transfers.ledger.LedgerEntryRepository;
import com.example.simple_money_transfers.money.InvalidMoneyException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Fast, Spring-free coverage of every rejection branch in {@link TransferService#execute}, with
 * mocked repositories - complements {@link TransferServiceIT}'s H2-backed happy-path and atomicity
 * coverage.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

  @Mock private AccountRepository accountRepository;

  @Mock private TransferRepository transferRepository;

  @Mock private LedgerEntryRepository ledgerEntryRepository;

  @InjectMocks private TransferService transferService;

  /**
   * {@code Account.id} is JPA-generated with no public setter, so a plain {@code new Account(...)}
   * has a null id - fine for the real repository, which always returns rows already loaded from the
   * database, but {@link TransferService#execute} matches accounts by id, so a test double standing
   * in for a loaded row needs one set the same way Hibernate would.
   */
  private static Account account(
      UUID id, AccountType type, String currency, AccountStatus status, BigDecimal balance) {
    Account account = new Account("REF", "Holder", type, currency, status);
    account.setBalance(balance);
    ReflectionTestUtils.setField(account, "id", id);
    return account;
  }

  @Test
  void selfTransferIsRejectedBeforeTouchingTheDatabase() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                transferService.execute(
                    new TransferCommand(
                        id, id, new BigDecimal("10.00"), "USD", TransferKind.TRANSFER, null)))
        .isInstanceOf(SelfTransferException.class);

    verifyNoInteractions(accountRepository, transferRepository, ledgerEntryRepository);
  }

  @Test
  void nonPositiveAmountIsRejectedBeforeTouchingTheDatabase() {
    assertThatThrownBy(
            () ->
                transferService.execute(
                    new TransferCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("0.00"),
                        "USD",
                        TransferKind.TRANSFER,
                        null)))
        .isInstanceOf(InvalidMoneyException.class);

    verifyNoInteractions(accountRepository, transferRepository, ledgerEntryRepository);
  }

  @Test
  void overScaleAmountIsRejectedBeforeTouchingTheDatabase() {
    assertThatThrownBy(
            () ->
                transferService.execute(
                    new TransferCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("10.999"),
                        "USD",
                        TransferKind.TRANSFER,
                        null)))
        .isInstanceOf(InvalidMoneyException.class);

    verifyNoInteractions(accountRepository, transferRepository, ledgerEntryRepository);
  }

  @Test
  void missingAccountIsRejected() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Account source =
        account(
            sourceId,
            AccountType.CUSTOMER,
            "USD",
            AccountStatus.ACTIVE,
            new BigDecimal("100.0000"));
    when(accountRepository.lockAllById(List.of(sourceId, targetId))).thenReturn(List.of(source));

    assertThatThrownBy(
            () ->
                transferService.execute(
                    new TransferCommand(
                        sourceId,
                        targetId,
                        new BigDecimal("10.00"),
                        "USD",
                        TransferKind.TRANSFER,
                        null)))
        .isInstanceOf(NotFoundException.class);

    verify(transferRepository, never()).save(any());
  }

  @Test
  void inactiveSourceAccountIsRejected() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Account source =
        account(
            sourceId,
            AccountType.CUSTOMER,
            "USD",
            AccountStatus.FROZEN,
            new BigDecimal("100.0000"));
    Account target =
        account(
            targetId, AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE, new BigDecimal("0.0000"));
    when(accountRepository.lockAllById(List.of(sourceId, targetId)))
        .thenReturn(List.of(source, target));

    assertThatThrownBy(
            () ->
                transferService.execute(
                    new TransferCommand(
                        sourceId,
                        targetId,
                        new BigDecimal("10.00"),
                        "USD",
                        TransferKind.TRANSFER,
                        null)))
        .isInstanceOf(InactiveAccountException.class);

    verify(transferRepository, never()).save(any());
  }

  @Test
  void currencyMismatchIsRejected() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Account source =
        account(
            sourceId,
            AccountType.CUSTOMER,
            "USD",
            AccountStatus.ACTIVE,
            new BigDecimal("100.0000"));
    Account target =
        account(
            targetId, AccountType.CUSTOMER, "EUR", AccountStatus.ACTIVE, new BigDecimal("0.0000"));
    when(accountRepository.lockAllById(List.of(sourceId, targetId)))
        .thenReturn(List.of(source, target));

    assertThatThrownBy(
            () ->
                transferService.execute(
                    new TransferCommand(
                        sourceId,
                        targetId,
                        new BigDecimal("10.00"),
                        "USD",
                        TransferKind.TRANSFER,
                        null)))
        .isInstanceOf(CurrencyMismatchException.class);

    verify(transferRepository, never()).save(any());
  }

  @Test
  void insufficientFundsIsRejectedForACustomerSource() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Account source =
        account(
            sourceId, AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE, new BigDecimal("5.0000"));
    Account target =
        account(
            targetId, AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE, new BigDecimal("0.0000"));
    when(accountRepository.lockAllById(List.of(sourceId, targetId)))
        .thenReturn(List.of(source, target));

    assertThatThrownBy(
            () ->
                transferService.execute(
                    new TransferCommand(
                        sourceId,
                        targetId,
                        new BigDecimal("10.00"),
                        "USD",
                        TransferKind.TRANSFER,
                        null)))
        .isInstanceOf(InsufficientFundsException.class);

    verify(transferRepository, never()).save(any());
  }

  @Test
  void systemSourceIsExemptFromInsufficientFundsCheck() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    Account source =
        account(
            sourceId, AccountType.SYSTEM, "USD", AccountStatus.ACTIVE, new BigDecimal("0.0000"));
    Account target =
        account(
            targetId, AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE, new BigDecimal("0.0000"));
    when(accountRepository.lockAllById(List.of(sourceId, targetId)))
        .thenReturn(List.of(source, target));
    when(transferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    transferService.execute(
        new TransferCommand(
            sourceId, targetId, new BigDecimal("50.00"), "USD", TransferKind.DEPOSIT, null));

    verify(transferRepository).save(any());
  }
}
