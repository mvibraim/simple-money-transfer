package com.example.simple_money_transfers.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Constructs the {@link JsonMapper} directly, applying the same customizer
 * Spring would wire as a bean, so this stays a pure unit test with no
 * Spring context.
 */
class MoneyJacksonConfigTest {

	private final JsonMapper mapper = buildMapper();

	private static JsonMapper buildMapper() {
		JsonMapper.Builder builder = JsonMapper.builder();
		new MoneyJacksonConfig().bigDecimalAsStringCustomizer().customize(builder);
		return builder.build();
	}

	@Test
	void serializesBigDecimalAsAJsonString() {
		String json = mapper.writeValueAsString(new BigDecimal("10.5000"));
		assertThat(json).isEqualTo("\"10.5000\"");
	}

	@Test
	void serializesAZeroAmountAsPlainNotation() {
		String json = mapper.writeValueAsString(new BigDecimal("0.0000"));
		assertThat(json).isEqualTo("\"0.0000\"");
	}

}
