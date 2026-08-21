package com.example.simple_money_transfers.idempotency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A SHA-256 hex digest over the canonical form of a money-movement
 * request. Must be stable: the same logical request has to fingerprint
 * identically regardless of incidental differences (field construction
 * order, trailing zeros in the amount), or a legitimate retry would
 * spuriously conflict with itself. Must also be sensitive to any real
 * difference - this is what F14's "same key, different body -> 422"
 * behavior depends on.
 */
public final class RequestFingerprint {

	private RequestFingerprint() {
	}

	public static String of(String kind, UUID sourceAccountId, UUID targetAccountId, BigDecimal amount,
			String currency, String reference) {
		// Fixed scale, not the caller's raw scale: "10.00" and "10.0000"
		// must fingerprint identically, since a client may format the same
		// logical amount differently across a retry.
		String canonical = String.join("|",
				kind,
				sourceAccountId.toString(),
				targetAccountId.toString(),
				amount.setScale(4, RoundingMode.HALF_UP).toPlainString(),
				currency,
				reference == null ? "" : reference);
		return sha256Hex(canonical);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			// SHA-256 is mandated by every JDK's default security provider.
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
