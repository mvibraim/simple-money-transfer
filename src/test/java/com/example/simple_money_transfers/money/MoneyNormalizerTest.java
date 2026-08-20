package com.example.simple_money_transfers.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyNormalizerTest {

	@Test
	void acceptsAWholeAmountForAnyCurrency() {
		BigDecimal normalized = MoneyNormalizer.normalize(new BigDecimal("10"), "USD");
		assertThat(normalized).isEqualByComparingTo("10.0000");
		assertThat(normalized.scale()).isEqualTo(4);
	}

	@Test
	void acceptsAnAmountAtTheCurrencysMaxScale() {
		BigDecimal normalized = MoneyNormalizer.normalize(new BigDecimal("10.99"), "USD");
		assertThat(normalized).isEqualByComparingTo("10.9900");
	}

	@Test
	void rejectsAnAmountFinerThanTheCurrencyAllows() {
		assertThatThrownBy(() -> MoneyNormalizer.normalize(new BigDecimal("10.999"), "USD"))
			.isInstanceOf(InvalidAmountScaleException.class)
			.hasMessageContaining("USD")
			.hasMessageContaining("2");
	}

	@Test
	void rejectsAnyFractionForAZeroDecimalCurrency() {
		assertThatThrownBy(() -> MoneyNormalizer.normalize(new BigDecimal("10.5"), "JPY"))
			.isInstanceOf(InvalidAmountScaleException.class);
	}

	@Test
	void acceptsAThreeDecimalCurrencyAtItsFullPrecision() {
		BigDecimal normalized = MoneyNormalizer.normalize(new BigDecimal("10.123"), "KWD");
		assertThat(normalized).isEqualByComparingTo("10.1230");
	}

	@Test
	void rejectsAnUnknownCurrencyCode() {
		assertThatThrownBy(() -> MoneyNormalizer.normalize(new BigDecimal("10.00"), "XXX_NOT_REAL"))
			.isInstanceOf(InvalidCurrencyException.class)
			.hasMessageContaining("XXX_NOT_REAL");
	}

	@Test
	void requireValidCurrencyRejectsLowercaseNonMatchingCode() {
		assertThatThrownBy(() -> MoneyNormalizer.requireValidCurrency("nope"))
			.isInstanceOf(InvalidCurrencyException.class);
	}

	@Test
	void requireValidCurrencyAcceptsARealCode() {
		assertThat(MoneyNormalizer.requireValidCurrency("EUR").getCurrencyCode()).isEqualTo("EUR");
	}

}
