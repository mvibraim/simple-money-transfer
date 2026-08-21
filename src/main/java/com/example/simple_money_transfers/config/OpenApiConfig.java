package com.example.simple_money_transfers.config;

import java.math.BigDecimal;
import java.util.Set;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String API_KEY_SECURITY_SCHEME = "apiKey";

	private static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";

	static {
		// MoneyJacksonConfig serializes every BigDecimal as a JSON string, not a bare
		// number, so every money field (amount, balance, balanceAfter) must be
		// documented as type: string. This has to run in a static initializer,
		// before springdoc resolves any DTO schema.
		SpringDocUtils.getConfig().replaceWithSchema(BigDecimal.class, new StringSchema());
	}

	@Bean
	OpenAPI simpleMoneyTransfersOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Simple Money Transfers API")
				.description(
						"Accounts, deposits, withdrawals, and transfers backed by an append-only double-entry ledger.")
				.version("v1")
				.license(new License().name("Apache 2.0")))
			.components(new Components()
				.addSecuritySchemes(API_KEY_SECURITY_SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.APIKEY)
							.in(SecurityScheme.In.HEADER)
							.name(ApiKeyAuthFilter.HEADER_NAME))
				.addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema())
				.addResponses("Unauthorized", problemResponse("Missing or invalid API key"))
				.addResponses("ServerError", problemResponse("An unexpected error occurred")))
			.addSecurityItem(new SecurityRequirement().addList(API_KEY_SECURITY_SCHEME));
	}

	/**
	 * Every {@code ApiExceptionHandler} method returns {@code ProblemDetail} via
	 * {@code ProblemDetail.forStatusAndDetail}, which never calls {@code setType}/
	 * {@code setTitle} - so {@code type} stays {@code "about:blank"} and {@code title} is
	 * the HTTP reason phrase. {@code errors} is the one extension field, added only by
	 * the two validation handlers.
	 */
	private Schema<?> problemDetailSchema() {
		// .types(Set.of(...)), not the legacy single-string .type(...): swagger-core
		// 2.2.52's OpenAPI-3.1 model represents type as a Set<String>, and
		// bridging a manually-built Schema's legacy string type through springdoc's
		// internal JSON-clone step silently drops it (with a logged warning).
		return new Schema<>().types(Set.of("object"))
			.description("RFC 9457 problem details, as returned by every error response.")
			.addProperty("type", new StringSchema().example("about:blank"))
			.addProperty("title", new StringSchema().example("Unprocessable Content"))
			.addProperty("status", new Schema<>().types(Set.of("integer")).example(422))
			.addProperty("detail", new StringSchema().example("Insufficient funds"))
			.addProperty("instance", new StringSchema())
			.addProperty("errors", new Schema<>().types(Set.of("array"))
				.items(new StringSchema())
				.description("Present only on 400 validation failures: one \"field: message\" entry per violation."));
	}

	private ApiResponse problemResponse(String description) {
		return new ApiResponse().description(description)
			.content(new Content().addMediaType("application/problem+json",
					new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL_SCHEMA))));
	}

	/**
	 * 401 and 500 apply uniformly to every operation (missing/invalid API key, unmapped
	 * exception), so they're added here once instead of on every {@code @ApiResponse} in
	 * every controller method.
	 */
	@Bean
	OperationCustomizer commonErrorResponsesCustomizer() {
		return (operation, handlerMethod) -> {
			ApiResponses responses = operation.getResponses();
			responses.addApiResponse("401", new ApiResponse().$ref("#/components/responses/Unauthorized"));
			responses.addApiResponse("500", new ApiResponse().$ref("#/components/responses/ServerError"));
			return operation;
		};
	}

}
