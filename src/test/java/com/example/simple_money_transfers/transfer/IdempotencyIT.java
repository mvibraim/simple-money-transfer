package com.example.simple_money_transfers.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.simple_money_transfers.account.Account;
import com.example.simple_money_transfers.account.AccountRepository;
import com.example.simple_money_transfers.account.AccountStatus;
import com.example.simple_money_transfers.account.AccountType;
import com.example.simple_money_transfers.config.ApiKeyAuthFilter;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;
import com.example.simple_money_transfers.support.LedgerInvariants;

@AutoConfigureMockMvc
class IdempotencyIT extends AbstractIntegrationTest {

	private static final String VALID_KEY = "test-api-key-that-is-at-least-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransferService transferService;

	@Autowired
	private JdbcClient jdbcClient;

	@AfterEach
	void ledgerInvariantsAlwaysHold() {
		LedgerInvariants.assertAll(jdbcClient);
	}

	private Account fundedAccount(String ref, BigDecimal balance, String currency) {
		Account account = accountRepository
			.save(new Account(ref, "Holder " + ref, AccountType.CUSTOMER, currency, AccountStatus.ACTIVE));
		if (balance.signum() > 0) {
			transferService.deposit(account.getId(), balance, null);
		}
		return accountRepository.findById(account.getId()).orElseThrow();
	}

	private String transferBody(UUID sourceId, UUID targetId, String amount) {
		return """
				{"sourceAccountId":"%s","targetAccountId":"%s","amount":"%s","currency":"USD"}
				""".formatted(sourceId, targetId, amount);
	}

	@Test
	void replayWithAnIdenticalBodyReturnsTheSameTransferAndMovesMoneyOnce() throws Exception {
		Account source = fundedAccount("R1", new BigDecimal("100.00"), "USD");
		Account target = fundedAccount("R2", new BigDecimal("0.00"), "USD");
		String key = UUID.randomUUID().toString();
		String body = transferBody(source.getId(), target.getId(), "30.00");

		MvcResult first = mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();

		MvcResult second = mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();

		assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
		assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("70.0000");
		assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("30.0000");

		// Excludes fundedAccount's own setup deposit - only the TRANSFER
		// under test should ever produce a second transfer row if replay
		// were broken.
		Long transferCount = jdbcClient.sql("SELECT COUNT(*) FROM transfer WHERE kind = 'TRANSFER'")
			.query(Long.class)
			.single();
		assertThat(transferCount).isEqualTo(1);
	}

	@Test
	void sameKeyDifferentAmountIsRejectedWith422() throws Exception {
		Account source = fundedAccount("F1", new BigDecimal("100.00"), "USD");
		Account target = fundedAccount("F2", new BigDecimal("0.00"), "USD");
		String key = UUID.randomUUID().toString();

		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(transferBody(source.getId(), target.getId(), "10.00")))
			.andExpect(status().isCreated());

		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(transferBody(source.getId(), target.getId(), "20.00")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void concurrentRequestsWithTheSameKeyProduceExactlyOneTransfer() throws Exception {
		Account source = fundedAccount("C1", new BigDecimal("100.00"), "USD");
		Account target = fundedAccount("C2", new BigDecimal("0.00"), "USD");
		String key = UUID.randomUUID().toString();
		String body = transferBody(source.getId(), target.getId(), "10.00");
		int concurrency = 8;

		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		try {
			List<Callable<Integer>> tasks = IntStream.range(0, concurrency)
				.<Callable<Integer>>mapToObj(i -> () -> mockMvc
					.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
						.header("Idempotency-Key", key)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
					.andReturn()
					.getResponse()
					.getStatus())
				.toList();

			List<Future<Integer>> futures = executor.invokeAll(tasks);
			List<Integer> statuses = futures.stream().map(f -> {
				try {
					return f.get();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}).toList();

			assertThat(statuses).allMatch(status -> status == 201);
		}
		finally {
			executor.shutdown();
		}

		Long transferCount = jdbcClient.sql("SELECT COUNT(*) FROM transfer WHERE source_account_id = ?")
			.param(source.getId())
			.query(Long.class)
			.single();
		assertThat(transferCount).isEqualTo(1);
		assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("90.0000");
	}

	@Test
	void keyIsReusableAfterAFailedAttempt() throws Exception {
		Account source = fundedAccount("K1", new BigDecimal("5.00"), "USD");
		Account target = fundedAccount("K2", new BigDecimal("0.00"), "USD");
		String key = UUID.randomUUID().toString();
		String body = transferBody(source.getId(), target.getId(), "10.00");

		// First attempt: rejected for insufficient funds. The claim rolls
		// back with the transaction, so the key is never recorded as used.
		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnprocessableContent());

		// Fund the account, then retry the identical request with the same key.
		transferService.deposit(source.getId(), new BigDecimal("10.00"), null);

		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated());

		assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
			.isEqualByComparingTo("10.0000");
	}

}
