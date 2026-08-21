package com.example.simple_money_transfers.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

	private static final UUID SOURCE = UUID.randomUUID();

	private static final UUID TARGET = UUID.randomUUID();

	@Test
	void isDeterministicForTheSameLogicalRequest() {
		String first = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", "rent");
		String second = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", "rent");
		assertThat(first).isEqualTo(second);
	}

	@Test
	void isStableAcrossIncidentalAmountScaleDifferences() {
		String scaleTwo = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", null);
		String scaleFour = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.0000"), "USD", null);
		String scaleZero = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10"), "USD", null);
		assertThat(scaleTwo).isEqualTo(scaleFour).isEqualTo(scaleZero);
	}

	@Test
	void looksLikeA256BitHexDigest() {
		String fingerprint = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", null);
		assertThat(fingerprint).hasSize(64);
		assertThat(Pattern.matches("[0-9a-f]{64}", fingerprint)).isTrue();
	}

	@Test
	void differsWhenTheAmountDiffers() {
		String ten = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", null);
		String eleven = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("11.00"), "USD", null);
		assertThat(ten).isNotEqualTo(eleven);
	}

	@Test
	void differsWhenTheCurrencyDiffers() {
		String usd = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", null);
		String eur = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "EUR", null);
		assertThat(usd).isNotEqualTo(eur);
	}

	@Test
	void differsWhenTheKindDiffers() {
		String transfer = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", null);
		String deposit = RequestFingerprint.of("DEPOSIT", SOURCE, TARGET, new BigDecimal("10.00"), "USD", null);
		assertThat(transfer).isNotEqualTo(deposit);
	}

	@Test
	void differsWhenTheSourceOrTargetAccountDiffers() {
		UUID otherTarget = UUID.randomUUID();
		String original = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", null);
		String differentTarget = RequestFingerprint.of("TRANSFER", SOURCE, otherTarget, new BigDecimal("10.00"), "USD",
				null);
		assertThat(original).isNotEqualTo(differentTarget);
	}

	@Test
	void differsWhenTheReferenceDiffers() {
		String withRef = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", "rent");
		String withoutRef = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD", null);
		String differentRef = RequestFingerprint.of("TRANSFER", SOURCE, TARGET, new BigDecimal("10.00"), "USD",
				"invoice");
		assertThat(withRef).isNotEqualTo(withoutRef).isNotEqualTo(differentRef);
	}

}
