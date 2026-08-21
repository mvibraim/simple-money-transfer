package com.example.simple_money_transfers.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.example.simple_money_transfers.exception.InvalidCursorException;

class CursorCodecTest {

	private static final Base64.Encoder RAW_ENCODER = Base64.getUrlEncoder().withoutPadding();

	@Test
	void roundTripsAnEntryId() {
		String cursor = CursorCodec.encode(104L);
		assertThat(CursorCodec.decode(cursor)).isEqualTo(104L);
	}

	@Test
	void roundTripsZero() {
		String cursor = CursorCodec.encode(0L);
		assertThat(CursorCodec.decode(cursor)).isEqualTo(0L);
	}

	@Test
	void rejectsNonBase64Input() {
		assertThatThrownBy(() -> CursorCodec.decode("not base64!!")).isInstanceOf(InvalidCursorException.class)
			.hasMessageContaining("not base64!!");
	}

	@Test
	void rejectsAPayloadWithNoVersionPrefix() {
		String noPrefix = RAW_ENCODER.encodeToString("104".getBytes(StandardCharsets.UTF_8));
		assertThatThrownBy(() -> CursorCodec.decode(noPrefix)).isInstanceOf(InvalidCursorException.class);
	}

	@Test
	void rejectsAnUnknownVersionPrefix() {
		String wrongVersion = RAW_ENCODER.encodeToString("v2:104".getBytes(StandardCharsets.UTF_8));
		assertThatThrownBy(() -> CursorCodec.decode(wrongVersion)).isInstanceOf(InvalidCursorException.class);
	}

	@Test
	void rejectsANonNumericId() {
		String nonNumeric = RAW_ENCODER.encodeToString("v1:abc".getBytes(StandardCharsets.UTF_8));
		assertThatThrownBy(() -> CursorCodec.decode(nonNumeric)).isInstanceOf(InvalidCursorException.class);
	}

	@Test
	void rejectsANegativeId() {
		String negative = RAW_ENCODER.encodeToString("v1:-1".getBytes(StandardCharsets.UTF_8));
		assertThatThrownBy(() -> CursorCodec.decode(negative)).isInstanceOf(InvalidCursorException.class);
	}

}
