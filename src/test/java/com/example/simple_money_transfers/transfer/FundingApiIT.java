package com.example.simple_money_transfers.transfer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import com.example.simple_money_transfers.account.Account;
import com.example.simple_money_transfers.account.AccountRepository;
import com.example.simple_money_transfers.account.AccountStatus;
import com.example.simple_money_transfers.account.AccountType;
import com.example.simple_money_transfers.config.ApiKeyAuthFilter;
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

}
