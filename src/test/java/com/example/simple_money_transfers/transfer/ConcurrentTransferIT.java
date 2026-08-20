package com.example.simple_money_transfers.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.simple_money_transfers.account.Account;
import com.example.simple_money_transfers.account.AccountRepository;
import com.example.simple_money_transfers.account.AccountStatus;
import com.example.simple_money_transfers.account.AccountType;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;
import com.example.simple_money_transfers.support.LedgerInvariants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Proves F09's write path under real concurrent load: no lost updates, and the ledger stays
 * balanced. Runs on H2, not Postgres - see F00's testing-strategy section and this feature's own
 * spec for exactly what that does and doesn't prove. In particular, the ABBA and ring scenarios
 * exercise the ascending-UUID lock ordering that avoids deadlock, but H2 resolves lock contention
 * with a timeout rather than Postgres's deadlock detector, so this is evidence the design works,
 * not a substitute for running it against real Postgres.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}: Spring's test transaction support would wrap
 * the whole method in one transaction and roll it back, serializing every "concurrent" worker
 * behind it and making every invariant assertion pass vacuously.
 */
class ConcurrentTransferIT extends AbstractIntegrationTest {

  @Autowired private AccountRepository accountRepository;

  @Autowired private TransferService transferService;

  @Autowired private JdbcClient jdbcClient;

  @AfterEach
  void ledgerInvariantsAlwaysHold() {
    LedgerInvariants.assertAll(jdbcClient);
  }

  private Account customerAccount(String ref, String currency) {
    return accountRepository.save(
        new Account(ref, "Holder " + ref, AccountType.CUSTOMER, currency, AccountStatus.ACTIVE));
  }

  private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws InterruptedException {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      List<Future<T>> futures = executor.invokeAll(tasks);
      return futures.stream()
          .map(
              f -> {
                try {
                  return f.get();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              })
          .toList();
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void singleAccountDrainHasNoLostUpdates() throws Exception {
    Account source = customerAccount("DRAIN-S", "USD");
    Account target = customerAccount("DRAIN-T", "USD");
    transferService.deposit(source.getId(), new BigDecimal("50.00"), null);

    int requestCount = 32;
    BigDecimal amountPerTransfer = new BigDecimal("5.00");

    List<Callable<Boolean>> tasks =
        IntStream.range(0, requestCount)
            .<Callable<Boolean>>mapToObj(
                i ->
                    () -> {
                      try {
                        transferService.execute(
                            new TransferCommand(
                                source.getId(),
                                target.getId(),
                                amountPerTransfer,
                                "USD",
                                TransferKind.TRANSFER,
                                null));
                        return true;
                      } catch (InsufficientFundsException ex) {
                        return false;
                      }
                    })
            .toList();

    List<Boolean> outcomes = runConcurrently(tasks);
    long successCount = outcomes.stream().filter(Boolean::booleanValue).count();

    BigDecimal expectedDebited = amountPerTransfer.multiply(BigDecimal.valueOf(successCount));
    Account reloadedSource = accountRepository.findById(source.getId()).orElseThrow();
    Account reloadedTarget = accountRepository.findById(target.getId()).orElseThrow();

    assertThat(reloadedSource.getBalance())
        .isEqualByComparingTo(new BigDecimal("50.00").subtract(expectedDebited));
    assertThat(reloadedTarget.getBalance()).isEqualByComparingTo(expectedDebited);
    assertThat(reloadedSource.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }

  @Test
  void abbaOpposingTransfersDoNotDeadlockAndNetToZero() throws Exception {
    Account accountA = customerAccount("ABBA-A", "USD");
    Account accountB = customerAccount("ABBA-B", "USD");
    transferService.deposit(accountA.getId(), new BigDecimal("1000.00"), null);
    transferService.deposit(accountB.getId(), new BigDecimal("1000.00"), null);

    int perDirection = 16;
    BigDecimal amount = new BigDecimal("1.00");

    List<Callable<Void>> tasks = new ArrayList<>();
    for (int i = 0; i < perDirection; i++) {
      tasks.add(
          () -> {
            transferService.execute(
                new TransferCommand(
                    accountA.getId(),
                    accountB.getId(),
                    amount,
                    "USD",
                    TransferKind.TRANSFER,
                    null));
            return null;
          });
      tasks.add(
          () -> {
            transferService.execute(
                new TransferCommand(
                    accountB.getId(),
                    accountA.getId(),
                    amount,
                    "USD",
                    TransferKind.TRANSFER,
                    null));
            return null;
          });
    }

    runConcurrently(tasks);

    Account reloadedA = accountRepository.findById(accountA.getId()).orElseThrow();
    Account reloadedB = accountRepository.findById(accountB.getId()).orElseThrow();
    assertThat(reloadedA.getBalance()).isEqualByComparingTo("1000.00");
    assertThat(reloadedB.getBalance()).isEqualByComparingTo("1000.00");
  }

  @Test
  void ringOfAccountsUnderHighContentionStaysConsistent() throws Exception {
    int accountCount = 8;
    int requestCount = 64;
    BigDecimal startingBalance = new BigDecimal("1000.00");
    BigDecimal amount = new BigDecimal("1.00");

    List<Account> accounts =
        IntStream.range(0, accountCount)
            .mapToObj(i -> customerAccount("RING-" + i, "USD"))
            .toList();
    for (Account account : accounts) {
      transferService.deposit(account.getId(), startingBalance, null);
    }

    Random random = new Random(42);
    AtomicInteger successCount = new AtomicInteger();
    List<Callable<Void>> tasks =
        IntStream.range(0, requestCount)
            .<Callable<Void>>mapToObj(
                i -> {
                  int sourceIndex = random.nextInt(accountCount);
                  int targetIndex;
                  do {
                    targetIndex = random.nextInt(accountCount);
                  } while (targetIndex == sourceIndex);
                  UUID sourceId = accounts.get(sourceIndex).getId();
                  UUID targetId = accounts.get(targetIndex).getId();
                  return () -> {
                    transferService.execute(
                        new TransferCommand(
                            sourceId, targetId, amount, "USD", TransferKind.TRANSFER, null));
                    successCount.incrementAndGet();
                    return null;
                  };
                })
            .toList();

    runConcurrently(tasks);

    // Every account starts with ample funds relative to the total money
    // in play (8 * 1000 vs. 64 transfers of 1.00), so every transfer is
    // expected to succeed - a lost update would show up as a wrong count
    // or a balance mismatch below.
    assertThat(successCount.get()).isEqualTo(requestCount);

    BigDecimal totalAfter =
        accounts.stream()
            .map(a -> accountRepository.findById(a.getId()).orElseThrow().getBalance())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalBefore = startingBalance.multiply(BigDecimal.valueOf(accountCount));
    assertThat(totalAfter).isEqualByComparingTo(totalBefore);
  }
}
