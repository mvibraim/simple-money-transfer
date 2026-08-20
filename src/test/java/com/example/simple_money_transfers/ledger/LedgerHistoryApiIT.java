package com.example.simple_money_transfers.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.simple_money_transfers.account.Account;
import com.example.simple_money_transfers.account.AccountRepository;
import com.example.simple_money_transfers.account.AccountStatus;
import com.example.simple_money_transfers.account.AccountType;
import com.example.simple_money_transfers.config.ApiKeyAuthFilter;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;
import com.example.simple_money_transfers.support.LedgerInvariants;
import com.example.simple_money_transfers.transfer.TransferService;
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
		transferService.execute(new com.example.simple_money_transfers.transfer.TransferCommand(account.getId(),
				other.getId(), new BigDecimal("10.00"), "USD",
				com.example.simple_money_transfers.transfer.TransferKind.TRANSFER, null));
		transferService.execute(new com.example.simple_money_transfers.transfer.TransferCommand(account.getId(),
				other.getId(), new BigDecimal("5.00"), "USD",
				com.example.simple_money_transfers.transfer.TransferKind.TRANSFER, null));
		// account now has 3 ledger entries: +100 (deposit credit), -10, -5

		Set<Long> seenIds = new HashSet<>();
		int page = 0;
		int totalPages;
		do {
			MvcResult result = mockMvc
				.perform(get("/api/v1/accounts/" + account.getId() + "/entries").param("page", String.valueOf(page))
					.param("size", "2")
					.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
				.andExpect(status().isOk())
				.andReturn();

			JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
			body.path("entries").forEach(entry -> seenIds.add(entry.path("id").asLong()));
			totalPages = body.path("totalPages").asInt();
			assertThat(body.path("totalElements").asLong()).isEqualTo(3);
			page++;
		}
		while (page < totalPages);

		assertThat(seenIds).hasSize(3);
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
