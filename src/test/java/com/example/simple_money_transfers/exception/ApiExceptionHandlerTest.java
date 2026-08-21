package com.example.simple_money_transfers.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// This slice exists to test the error contract, not auth — Spring Security's
// filters (CSRF in particular) would otherwise reject these POSTs before they
// ever reach ProbeController. Auth itself is covered by ApiKeyAuthIT.
@WebMvcTest(controllers = ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void notFoundExceptionMapsTo404() throws Exception {
		mockMvc.perform(post("/probe/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("account 123 not found"));
	}

	@Test
	void businessRuleExceptionMapsTo422() throws Exception {
		mockMvc.perform(post("/probe/business-rule"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(422))
			.andExpect(jsonPath("$.detail").value("insufficient funds"));
	}

	@Test
	void beanValidationFailureMapsTo400() throws Exception {
		mockMvc.perform(post("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void malformedJsonMapsTo400() throws Exception {
		mockMvc.perform(post("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Malformed request body"));
	}

	@Test
	void malformedPathVariableMapsTo400() throws Exception {
		mockMvc.perform(get("/probe/type-mismatch/not-a-uuid"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Malformed value for parameter 'id'"));
	}

	@Test
	void invalidCursorExceptionMapsTo400() throws Exception {
		mockMvc.perform(post("/probe/invalid-cursor"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Malformed cursor: not-a-real-cursor"));
	}

	@Test
	void parameterValidationFailureMapsTo400() throws Exception {
		mockMvc.perform(get("/probe/param-validated").param("limit", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("limit")));
	}

	@Test
	void optimisticLockConflictMapsTo503() throws Exception {
		mockMvc.perform(post("/probe/optimistic-lock-conflict"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(503));
	}

	@Test
	void unexpectedExceptionMapsTo500WithNoLeakedDetail() throws Exception {
		mockMvc.perform(post("/probe/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
			.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
	}

}
