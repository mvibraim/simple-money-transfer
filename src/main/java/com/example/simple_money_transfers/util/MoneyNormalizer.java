package com.example.simple_money_transfers.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

import com.example.simple_money_transfers.exception.InvalidAmountScaleException;
import com.example.simple_money_transfers.exception.InvalidCurrencyException;

/**
 * The boundary check for every monetary amount entering the system.
 * <p>
 * Postgres silently rounds a {@code NUMERIC(19,4)} column on an over-scale insert rather
 * than raising an error, so this is the only thing standing between a client-supplied
 * amount and silent truncation.
 */
public final class MoneyNormalizer {

	private MoneyNormalizer() {
	}

	public static Currency requireValidCurrency(String currencyCode) {
		try {
			return Currency.getInstance(currencyCode);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new InvalidCurrencyException(currencyCode);
		}
	}

	/**
	 * Validates that {@code amount}'s scale does not exceed what {@code currencyCode}
	 * allows, then normalizes it to scale 4 for storage. Never rounds: once the scale
	 * check passes, widening to scale 4 cannot lose precision.
	 */
	public static BigDecimal normalize(BigDecimal amount, String currencyCode) {
		Currency currency = requireValidCurrency(currencyCode);
		int maxScale = currency.getDefaultFractionDigits();
		if (amount.scale() > maxScale) {
			throw new InvalidAmountScaleException(currencyCode, maxScale, amount.scale());
		}
		return amount.setScale(4, RoundingMode.UNNECESSARY);
	}

}
