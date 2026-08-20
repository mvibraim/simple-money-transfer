package com.example.simple_money_transfers.money;

import java.math.BigDecimal;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Amounts serialize as JSON strings, not bare numbers, so a JavaScript client (backed by
 * IEEE 754 doubles) can't lose precision before the value ever reaches this service.
 */
@Configuration
public class MoneyJacksonConfig {

	@Bean
	JsonMapperBuilderCustomizer bigDecimalAsStringCustomizer() {
		SimpleModule module = new SimpleModule();
		module.addSerializer(BigDecimal.class, new BigDecimalAsStringSerializer());
		return builder -> builder.addModule(module);
	}

	private static final class BigDecimalAsStringSerializer extends ValueSerializer<BigDecimal> {

		@Override
		public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
			gen.writeString(value.toPlainString());
		}

	}

}
