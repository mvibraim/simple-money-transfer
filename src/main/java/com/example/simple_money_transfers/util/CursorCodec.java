package com.example.simple_money_transfers.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.example.simple_money_transfers.exception.InvalidCursorException;

/**
 * Encodes a ledger entry id as an opaque, versioned pagination cursor. The {@code v1:}
 * prefix means a future change to the cursor's underlying key (e.g. a composite
 * timestamp+id) can reject old tokens outright instead of silently misreading them.
 */
public final class CursorCodec {

	private static final String VERSION_PREFIX = "v1:";

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private CursorCodec() {
	}

	public static String encode(long entryId) {
		return ENCODER.encodeToString((VERSION_PREFIX + entryId).getBytes(StandardCharsets.UTF_8));
	}

	public static long decode(String cursor) {
		String decoded;
		try {
			decoded = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidCursorException(cursor);
		}
		if (!decoded.startsWith(VERSION_PREFIX)) {
			throw new InvalidCursorException(cursor);
		}
		try {
			long entryId = Long.parseLong(decoded.substring(VERSION_PREFIX.length()));
			if (entryId < 0) {
				throw new InvalidCursorException(cursor);
			}
			return entryId;
		}
		catch (NumberFormatException ex) {
			throw new InvalidCursorException(cursor);
		}
	}

}
