package com.example.simple_money_transfers.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.simple_money_transfers.config.ApiKeyAuthFilter;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;

import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class AccountApiIT extends AbstractIntegrationTest {

	private static final String VALID_KEY = "test-api-key-that-is-at-least-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void createThenFetchRoundTrip() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/accounts")
						.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"holderName":"Alice","currency":"USD"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.holderName").value("Alice"))
				.andExpect(jsonPath("$.currency").value("USD"))
				.andExpect(jsonPath("$.balance").value("0.0000"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.accountType").value("CUSTOMER"))
				.andReturn();

		String id = jsonMapper.readTree(created.getResponse().getContentAsString()).path("id").asString();

		mockMvc.perform(get("/api/v1/accounts/" + id).header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.holderName").value("Alice"))
				.andExpect(jsonPath("$.id").value(id));

		mockMvc.perform(get("/api/v1/accounts/" + id + "/balance").header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value("0.0000"))
				.andExpect(jsonPath("$.currency").value("USD"))
				.andExpect(jsonPath("$.asOf").exists());
	}

	@Test
	void missingHolderNameIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/accounts")
						.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"holderName":"","currency":"USD"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void malformedCurrencyShapeIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/accounts")
						.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"holderName":"Alice","currency":"us"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownCurrencyCodeIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/accounts")
						.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"holderName":"Alice","currency":"ZZZ"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownAccountIdReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/" + java.util.UUID.randomUUID())
						.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
				.andExpect(status().isNotFound());
	}

	@Test
	void malformedAccountIdReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/not-a-uuid")
						.header(ApiKeyAuthFilter.HEADER_NAME, VALID_KEY))
				.andExpect(status().isBadRequest());
	}

	@Test
	void missingApiKeyIsRejectedOnEveryEndpoint() throws Exception {
		mockMvc.perform(post("/api/v1/accounts")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"holderName":"Alice","currency":"USD"}"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/accounts/" + java.util.UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

}
