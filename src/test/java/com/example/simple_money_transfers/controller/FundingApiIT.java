package com.example.simple_money_transfers.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.simple_money_transfers.config.ApiKeyAuthFilter;
import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountStatus;
import com.example.simple_money_transfers.model.entity.AccountType;
import com.example.simple_money_transfers.repository.AccountRepository;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;
import com.example.simple_money_transfers.support.LedgerInvariants;

@AutoConfigureMockMvc
class FundingApiIT extends AbstractIntegrationTest {

	private static final String VALID_KEY = "test-api-key-that-is-at-least-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcClient jdbcClient;

	@AfterEach
	void ledgerInvariantsAlwaysHold() {
		LedgerInvariants.assertAll(jdbcClient);
	}

	private static String freshIdempotencyKey() {
		return UUID.randomUUID().toString();
	}

	/**
	 * A {@link CountDownLatch} start barrier, not a bare
	 * {@code Executors.newVirtualThreadPerTaskExecutor().invokeAll(...)}: without it
	 * every worker could in principle run to completion before the next one starts, and a
	 * fully serialized run would pass every assertion below identically to a genuinely
	 * concurrent one. Waiting for every worker to report ready before releasing any of
	 * them is what makes these tests actually prove overlap.
	 */
	private List<Integer> postConcurrently(int concurrency, IntFunction<MockHttpServletRequestBuilder> requestFor)
			throws InterruptedException {
		CountDownLatch ready = new CountDownLatch(concurrency);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		try {
			List<Future<Integer>> futures = IntStream.range(0, concurrency).<Callable<Integer>>mapToObj(i -> () -> {
				ready.countDown();
				start.await();
				return mockMvc.perform(requestFor.apply(i)).andReturn().getResponse().getStatus();
			}).map(executor::submit).toList();

			ready.await();
			start.countDown();

			return futures.stream().map(future -> {
				try {
					return future.get();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}).toList();
		}
		finally {
			executor.shutdown();
		}
	}

	@Test
	void depositMovesTheBalanceAndKeepsTheLedgerBalanced() throws Exception {
		Account account = accountRepository
			.save(new Account("D1", "Depositor", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/deposits")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":"50.00","reference":"top-up"}"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.amount").value("50.0000"))
			.andExpect(jsonPath("$.kind").value("DEPOSIT"));

		Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(reloaded.getBalance()).isEqualByComparingTo("50.0000");
	}

	@Test
	void withdrawalBelowBalanceIsRejectedWith422() throws Exception {
		Account account = accountRepository
			.save(new Account("W1", "Withdrawer", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/withdrawals")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":"10.00"}"""))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void depositIntoAnUnsupportedCurrencyIsRejectedWith422() throws Exception {
		Account account = accountRepository
			.save(new Account("K1", "Kuwaiti", AccountType.CUSTOMER, "KWD", AccountStatus.ACTIVE));

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/deposits")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":"10.00"}"""))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void nonPositiveAmountIsRejectedWith400() throws Exception {
		Account account = accountRepository
			.save(new Account("D2", "Depositor", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/deposits")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":"-5.00"}"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingIdempotencyKeyIsRejectedWith400() throws Exception {
		Account account = accountRepository
			.save(new Account("D4", "Depositor", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/deposits")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":"10.00"}"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingApiKeyIsRejected() throws Exception {
		Account account = accountRepository
			.save(new Account("D3", "Depositor", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/deposits").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":"10.00"}"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void depositThenWithdrawalRoundTripsToZero() throws Exception {
		Account account = accountRepository
			.save(new Account("R1", "RoundTrip", AccountType.CUSTOMER, "EUR", AccountStatus.ACTIVE));

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/deposits")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":"75.00"}"""))
			.andExpect(status().isCreated());

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/withdrawals")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"amount":"75.00"}"""))
			.andExpect(status().isCreated());

		Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(reloaded.getBalance()).isEqualByComparingTo("0.0000");
	}

	@Test
	void replayingTheSameDepositKeyReturnsTheSameResultWithoutDoublingTheBalance() throws Exception {
		Account account = accountRepository
			.save(new Account("D5", "Depositor", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		String key = freshIdempotencyKey();
		String body = """
				{"amount":"20.00"}""";

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/deposits")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());

		mockMvc
			.perform(post("/api/v1/accounts/" + account.getId() + "/deposits")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());

		Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(reloaded.getBalance()).isEqualByComparingTo("20.0000");
	}

	/**
	 * Regression test for the funding path pre-reading accounts unlocked before
	 * {@code TransferService.execute}'s lock query: each request's own unlocked read of
	 * this shared target account can be made stale by another request committing in
	 * between, which previously surfaced as an unhandled
	 * {@code ObjectOptimisticLockingFailureException} (500) instead of the pessimistic
	 * lock simply serializing the two requests.
	 */
	@Test
	void concurrentDepositsToTheSameAccountAllSucceed() throws Exception {
		Account account = accountRepository
			.save(new Account("CD1", "Concurrent", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		int concurrency = 8;
		BigDecimal amountEach = new BigDecimal("10.00");

		List<Integer> statuses = postConcurrently(concurrency,
				i -> post("/api/v1/accounts/" + account.getId() + "/deposits")
					.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
					.header("Idempotency-Key", freshIdempotencyKey())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"amount":"10.00"}"""));

		assertThat(statuses).hasSize(concurrency).allMatch(status -> status == 201);
		Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getBalance()).isEqualByComparingTo(amountEach.multiply(BigDecimal.valueOf(concurrency)));
	}

	/**
	 * Same underlying bug as {@link #concurrentDepositsToTheSameAccountAllSucceed}, but
	 * the shared row racing across requests is the per-currency SYSTEM account rather
	 * than a customer account - every deposit and withdrawal in a currency touches it, so
	 * concurrent funding to completely different customer accounts was equally affected.
	 */
	@Test
	void concurrentDepositsToDifferentAccountsInTheSameCurrencyAllSucceed() throws Exception {
		int concurrency = 8;
		List<Account> accounts = IntStream.range(0, concurrency)
			.mapToObj(i -> accountRepository
				.save(new Account("CD2-" + i, "Concurrent " + i, AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE)))
			.toList();
		BigDecimal amountEach = new BigDecimal("10.00");

		List<Integer> statuses = postConcurrently(concurrency,
				i -> post("/api/v1/accounts/" + accounts.get(i).getId() + "/deposits")
					.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
					.header("Idempotency-Key", freshIdempotencyKey())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"amount":"10.00"}"""));

		assertThat(statuses).hasSize(concurrency).allMatch(status -> status == 201);
		for (Account account : accounts) {
			Account reloaded = accountRepository.findById(account.getId()).orElseThrow();
			assertThat(reloaded.getBalance()).isEqualByComparingTo(amountEach);
		}
	}

}
