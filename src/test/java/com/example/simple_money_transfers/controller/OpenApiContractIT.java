package com.example.simple_money_transfers.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.simple_money_transfers.support.AbstractIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class OpenApiContractIT extends AbstractIntegrationTest {

	private static final String SNAPSHOT_PATH = "src/test/resources/openapi.json";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void apiDocsExposesEveryEndpointWithoutAuthentication() throws Exception {
		JsonNode paths = fetchApiDocs().path("paths");
		assertThat(paths.propertyNames()).containsExactlyInAnyOrder("/api/v1/accounts", "/api/v1/accounts/{id}",
				"/api/v1/accounts/{id}/balance", "/api/v1/accounts/{id}/deposits", "/api/v1/accounts/{id}/withdrawals",
				"/api/v1/accounts/{id}/entries", "/api/v1/transfers", "/api/v1/transfers/{id}");
	}

	/**
	 * Guards against undocumented contract drift - a changed path, status code, or schema
	 * that nobody updated the annotations for. After a deliberate API change, regenerate
	 * the snapshot with:
	 * {@code ./gradlew test --tests '*OpenApiContractIT' -PupdateOpenApiSnapshot} then
	 * review the diff to {@code src/test/resources/openapi.json} before committing it.
	 */
	@Test
	void apiDocsMatchesCommittedSnapshot() throws Exception {
		JsonNode actual = fetchApiDocs();
		if (Boolean.getBoolean("updateOpenApiSnapshot")) {
			Files.writeString(Path.of(SNAPSHOT_PATH),
					jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual));
			return;
		}
		JsonNode expected = jsonMapper.readTree(new ClassPathResource("openapi.json").getInputStream());
		assertThat(actual).isEqualTo(expected);
	}

	private JsonNode fetchApiDocs() throws Exception {
		MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		return jsonMapper.readTree(result.getResponse().getContentAsString());
	}

}
