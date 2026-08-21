package com.example.simple_money_transfers.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.simple_money_transfers.config.ApiKeyAuthFilter;
import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountStatus;
import com.example.simple_money_transfers.model.entity.AccountType;
import com.example.simple_money_transfers.repository.AccountRepository;
import com.example.simple_money_transfers.service.TransferService;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;
import com.example.simple_money_transfers.support.LedgerInvariants;

import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class TransferApiIT extends AbstractIntegrationTest {

	private static final String VALID_KEY = "test-api-key-that-is-at-least-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransferService transferService;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private JsonMapper jsonMapper;

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

	private static String freshIdempotencyKey() {
		return UUID.randomUUID().toString();
	}

	@Test
	void createThenFetchRoundTrip() throws Exception {
		Account source = fundedAccount("S1", new BigDecimal("100.00"), "USD");
		Account target = fundedAccount("T1", new BigDecimal("0.00"), "USD");

		MvcResult created = mockMvc.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
			.header("Idempotency-Key", freshIdempotencyKey())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceAccountId":"%s","targetAccountId":"%s","amount":"25.00","currency":"USD","reference":"rent"}
					""".formatted(source.getId(), target.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.amount").value("25.0000"))
			.andExpect(jsonPath("$.currency").value("USD"))
			.andExpect(jsonPath("$.kind").value("TRANSFER"))
			.andExpect(jsonPath("$.reference").value("rent"))
			.andReturn();

		String id = jsonMapper.readTree(created.getResponse().getContentAsString()).path("id").asString();

		mockMvc.perform(get("/api/v1/transfers/" + id).header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id));
	}

	@Test
	void insufficientFundsMapsTo422() throws Exception {
		Account source = fundedAccount("S2", new BigDecimal("10.00"), "USD");
		Account target = fundedAccount("T2", new BigDecimal("0.00"), "USD");

		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sourceAccountId":"%s","targetAccountId":"%s","amount":"30.00","currency":"USD"}
						""".formatted(source.getId(), target.getId())))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void currencyMismatchMapsTo422() throws Exception {
		Account source = fundedAccount("S3", new BigDecimal("100.00"), "USD");
		Account target = fundedAccount("T3", new BigDecimal("0.00"), "EUR");

		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sourceAccountId":"%s","targetAccountId":"%s","amount":"10.00","currency":"USD"}
						""".formatted(source.getId(), target.getId())))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void selfTransferMapsTo422() throws Exception {
		Account account = fundedAccount("S4", new BigDecimal("100.00"), "USD");

		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sourceAccountId":"%s","targetAccountId":"%s","amount":"10.00","currency":"USD"}
						""".formatted(account.getId(), account.getId())))
			.andExpect(status().isUnprocessableContent());
	}

	@Test
	void unknownAccountMapsTo404() throws Exception {
		Account source = fundedAccount("S5", new BigDecimal("100.00"), "USD");

		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sourceAccountId":"%s","targetAccountId":"%s","amount":"10.00","currency":"USD"}
						""".formatted(source.getId(), UUID.randomUUID())))
			.andExpect(status().isNotFound());
	}

	@Test
	void unknownTransferIdReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/transfers/" + UUID.randomUUID()).header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
			.andExpect(status().isNotFound());
	}

	@Test
	void missingFieldsAreRejectedWith400() throws Exception {
		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.header("Idempotency-Key", freshIdempotencyKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingIdempotencyKeyIsRejectedWith400() throws Exception {
		Account source = fundedAccount("S6", new BigDecimal("100.00"), "USD");
		Account target = fundedAccount("T6", new BigDecimal("0.00"), "USD");

		mockMvc
			.perform(post("/api/v1/transfers").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"sourceAccountId":"%s","targetAccountId":"%s","amount":"10.00","currency":"USD"}
						""".formatted(source.getId(), target.getId())))
			.andExpect(status().isBadRequest());
	}

	@Test
	void missingApiKeyIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
