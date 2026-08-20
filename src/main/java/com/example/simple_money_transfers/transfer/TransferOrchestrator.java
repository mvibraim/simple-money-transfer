package com.example.simple_money_transfers.transfer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.simple_money_transfers.idempotency.IdempotencyRecord;
import com.example.simple_money_transfers.idempotency.IdempotencyRecordRepository;
import com.example.simple_money_transfers.idempotency.RequestFingerprint;

/**
 * Makes every money-moving endpoint safe to retry. The claim row lives in
 * the same transaction as the transfer it belongs to (see {@link
 * IdempotentTransferAttempt}), not a separate "IN_PROGRESS" pre-claim:
 * because both H2 and Postgres block a second inserter on the unique
 * index until the first transaction resolves, concurrency is handled by
 * the database's own locking rather than application-level state. A
 * business rejection rolls back the claim along with the transfer, so the
 * key stays reusable - only successful transfers are ever memoized.
 * <p>
 * Idempotency records are never reaped. A TTL here would be a live
 * double-spend window: delete a record, and a client's legitimate retry
 * of that key executes a second, real transfer.
 */
@Component
public class TransferOrchestrator {

	private final TransferService transferService;
	private final IdempotentTransferAttempt idempotentTransferAttempt;
	private final IdempotencyRecordRepository idempotencyRecordRepository;

	public TransferOrchestrator(TransferService transferService, IdempotentTransferAttempt idempotentTransferAttempt,
			IdempotencyRecordRepository idempotencyRecordRepository) {
		this.transferService = transferService;
		this.idempotentTransferAttempt = idempotentTransferAttempt;
		this.idempotencyRecordRepository = idempotencyRecordRepository;
	}

	@Transactional(propagation = Propagation.NEVER)
	public ResponseEntity<String> transfer(String clientId, String idempotencyKey, CreateTransferRequest request) {
		TransferCommand command = new TransferCommand(request.sourceAccountId(), request.targetAccountId(),
				request.amount(), request.currency(), TransferKind.TRANSFER, request.reference());
		String fingerprint = RequestFingerprint.of("TRANSFER", request.sourceAccountId(), request.targetAccountId(),
				request.amount(), request.currency(), request.reference());
		return attemptOrReplay(clientId, idempotencyKey, fingerprint, () -> transferService.execute(command));
	}

	@Transactional(propagation = Propagation.NEVER)
	public ResponseEntity<String> deposit(String clientId, String idempotencyKey, UUID accountId, DepositRequest request) {
		String fingerprint = depositOrWithdrawalFingerprint("DEPOSIT", accountId, request.amount(), request.reference());
		return attemptOrReplay(clientId, idempotencyKey, fingerprint,
				() -> transferService.deposit(accountId, request.amount(), request.reference()));
	}

	@Transactional(propagation = Propagation.NEVER)
	public ResponseEntity<String> withdraw(String clientId, String idempotencyKey, UUID accountId, WithdrawalRequest request) {
		String fingerprint = depositOrWithdrawalFingerprint("WITHDRAWAL", accountId, request.amount(), request.reference());
		return attemptOrReplay(clientId, idempotencyKey, fingerprint,
				() -> transferService.withdraw(accountId, request.amount(), request.reference()));
	}

	private static String depositOrWithdrawalFingerprint(String kind, UUID accountId, BigDecimal amount, String reference) {
		// No currency field on these requests (F11): the account's own
		// currency is authoritative and already fully determined by
		// accountId, so a fixed placeholder loses no uniqueness here.
		return RequestFingerprint.of(kind, accountId, accountId, amount, "N/A", reference);
	}

	private ResponseEntity<String> attemptOrReplay(String clientId, String idempotencyKey, String fingerprint,
			Supplier<Transfer> action) {
		try {
			return idempotentTransferAttempt.run(clientId, idempotencyKey, fingerprint, action);
		}
		catch (DataIntegrityViolationException ex) {
			// The only plausible source of this exception within run()'s
			// scope is the idempotency-key unique constraint - action's own
			// work always creates a fresh transfer id, so it can never
			// collide with anything F08's constraints protect.
			return replayExisting(clientId, idempotencyKey, fingerprint);
		}
	}

	private ResponseEntity<String> replayExisting(String clientId, String idempotencyKey, String fingerprint) {
		IdempotencyRecord existing = idempotencyRecordRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
				.orElseThrow(() -> new IllegalStateException(
						"Expected an idempotency record for key %s after a unique-constraint violation, found none"
								.formatted(idempotencyKey)));
		if (!existing.getFingerprint().equals(fingerprint)) {
			throw new IdempotencyConflictException(idempotencyKey);
		}
		return ResponseEntity.status(existing.getResponseStatus())
				.contentType(MediaType.APPLICATION_JSON)
				.body(existing.getResponseBody());
	}

}
