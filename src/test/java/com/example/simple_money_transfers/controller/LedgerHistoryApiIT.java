package com.example.simple_money_transfers.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class LedgerHistoryApiIT extends AbstractIntegrationTest {

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

	@Test
	void returnsEveryEntryTouchingTheAccountAcrossPages() throws Exception {
		Account account = accountRepository
			.save(new Account("H1", "History", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		Account other = accountRepository
			.save(new Account("H2", "Other", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		transferService.deposit(account.getId(), new BigDecimal("100.00"), null);
		transferService.execute(new com.example.simple_money_transfers.model.dto.TransferCommand(account.getId(),
				other.getId(), new BigDecimal("10.00"), "USD",
				com.example.simple_money_transfers.model.entity.TransferKind.TRANSFER, null));
		transferService.execute(new com.example.simple_money_transfers.model.dto.TransferCommand(account.getId(),
				other.getId(), new BigDecimal("5.00"), "USD",
				com.example.simple_money_transfers.model.entity.TransferKind.TRANSFER, null));
		// account now has 3 ledger entries: +100 (deposit credit), -10, -5

		Set<Long> seenIds = new HashSet<>();
		String cursor = null;
		boolean hasMore = true;
		int iterations = 0;
		while (hasMore) {
			assertThat(iterations++).as("paging should terminate well before this many iterations").isLessThan(10);

			var requestBuilder = get("/api/v1/accounts/" + account.getId() + "/entries").param("limit", "2")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY);
			if (cursor != null) {
				requestBuilder.param("cursor", cursor);
			}

			MvcResult result = mockMvc.perform(requestBuilder).andExpect(status().isOk()).andReturn();

			JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
			body.path("entries").forEach(entry -> seenIds.add(entry.path("id").asLong()));
			hasMore = body.path("hasMore").asBoolean();
			cursor = hasMore ? body.path("nextCursor").asString() : null;
		}

		assertThat(seenIds).hasSize(3);
	}

	@Test
	void lastPageReportsNoMoreAndNoCursor() throws Exception {
		Account account = accountRepository
			.save(new Account("H5", "History", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		transferService.deposit(account.getId(), new BigDecimal("10.00"), null);

		mockMvc
			.perform(get("/api/v1/accounts/" + account.getId() + "/entries").header(ApiKeyAuthFilter.HEADER_NAME,
					VALID_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasMore").value(false))
			.andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void malformedCursorReturns400() throws Exception {
		Account account = accountRepository
			.save(new Account("H6", "History", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc
			.perform(get("/api/v1/accounts/" + account.getId() + "/entries").param("cursor", "not-a-real-cursor")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
			.andExpect(status().isBadRequest());
	}

	@Test
	void limitBelowMinimumReturns400() throws Exception {
		Account account = accountRepository
			.save(new Account("H7", "History", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc
			.perform(get("/api/v1/accounts/" + account.getId() + "/entries").param("limit", "0")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
			.andExpect(status().isBadRequest());
	}

	@Test
	void limitAboveMaximumReturns400() throws Exception {
		Account account = accountRepository
			.save(new Account("H8", "History", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc
			.perform(get("/api/v1/accounts/" + account.getId() + "/entries").param("limit", "101")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
			.andExpect(status().isBadRequest());
	}

	@Test
	void pagingIsStableAcrossAConcurrentInsertBetweenRequests() throws Exception {
		Account account = accountRepository
			.save(new Account("H9", "History", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		// Three entries exist before the client starts paging.
		transferService.deposit(account.getId(), new BigDecimal("1.00"), null);
		transferService.deposit(account.getId(), new BigDecimal("2.00"), null);
		transferService.deposit(account.getId(), new BigDecimal("3.00"), null);

		MvcResult firstPageResult = mockMvc
			.perform(get("/api/v1/accounts/" + account.getId() + "/entries").param("limit", "1")
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode firstPage = jsonMapper.readTree(firstPageResult.getResponse().getContentAsString());
		long firstPageId = firstPage.path("entries").get(0).path("id").asLong();
		String cursor = firstPage.path("nextCursor").asString();

		// A fourth entry lands above the cursor while the client is still
		// mid-walk - an offset-based page 2 would now be shifted by this
		// insert (skipping or duplicating a row); a cursor-based page 2 must
		// not be.
		transferService.deposit(account.getId(), new BigDecimal("4.00"), null);
		long fourthEntryId = jdbcClient.sql("SELECT id FROM ledger_entry WHERE account_id = ? ORDER BY id DESC LIMIT 1")
			.param(account.getId())
			.query(Long.class)
			.single();

		MvcResult secondPageResult = mockMvc
			.perform(get("/api/v1/accounts/" + account.getId() + "/entries").param("limit", "10")
				.param("cursor", cursor)
				.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode secondPage = jsonMapper.readTree(secondPageResult.getResponse().getContentAsString());

		Set<Long> secondPageIds = new HashSet<>();
		secondPage.path("entries").forEach(entry -> secondPageIds.add(entry.path("id").asLong()));

		// Exactly the two entries that existed below the cursor before the
		// concurrent insert - no duplicate of the first page's row, and no
		// leak of the newly-inserted row above it.
		assertThat(secondPageIds).hasSize(2).doesNotContain(firstPageId, fourthEntryId);
	}

	@Test
	void entriesAreOrderedNewestFirst() throws Exception {
		Account account = accountRepository
			.save(new Account("H3", "History", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		transferService.deposit(account.getId(), new BigDecimal("10.00"), null);
		transferService.deposit(account.getId(), new BigDecimal("20.00"), null);

		mockMvc
			.perform(get("/api/v1/accounts/" + account.getId() + "/entries").header(ApiKeyAuthFilter.HEADER_NAME,
					VALID_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.entries[0].amount").value("20.0000"))
			.andExpect(jsonPath("$.entries[1].amount").value("10.0000"));
	}

	@Test
	void unknownAccountReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/" + UUID.randomUUID() + "/entries").header(ApiKeyAuthFilter.HEADER_NAME,
				VALID_KEY))
			.andExpect(status().isNotFound());
	}

	@Test
	void missingApiKeyIsRejected() throws Exception {
		Account account = accountRepository
			.save(new Account("H4", "History", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));

		mockMvc.perform(get("/api/v1/accounts/" + account.getId() + "/entries")).andExpect(status().isUnauthorized());
	}

}
