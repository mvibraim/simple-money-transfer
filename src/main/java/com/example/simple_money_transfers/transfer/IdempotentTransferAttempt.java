package com.example.simple_money_transfers.transfer;

import java.net.URI;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.simple_money_transfers.idempotency.IdempotencyRecord;
import com.example.simple_money_transfers.idempotency.IdempotencyRecordRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * A separate bean from {@link TransferOrchestrator} on purpose: Spring's
 * transactional proxy only intercepts calls that arrive from a different
 * bean, so {@code @Transactional} here would be silently skipped if this
 * method lived on the orchestrator and were called via {@code this.}.
 * <p>
 * {@code action} and the idempotency claim insert run in one transaction.
 * If the claim insert's flush fails on the unique index, this whole
 * transaction rolls back - including whatever {@code action} already did
 * (locking accounts, mutating balances, writing ledger entries) - so a
 * losing concurrent duplicate never leaves a partial transfer behind.
 */
@Component
class IdempotentTransferAttempt {

	@PersistenceContext
	private EntityManager entityManager;

	private final IdempotencyRecordRepository idempotencyRecordRepository;
	private final JsonMapper jsonMapper;

	IdempotentTransferAttempt(IdempotencyRecordRepository idempotencyRecordRepository, JsonMapper jsonMapper) {
		this.idempotencyRecordRepository = idempotencyRecordRepository;
		this.jsonMapper = jsonMapper;
	}

	@Transactional
	ResponseEntity<String> run(String clientId, String idempotencyKey, String fingerprint, Supplier<Transfer> action) {
		Transfer transfer = action.get();
		String body = jsonMapper.writeValueAsString(TransferResponse.from(transfer));

		idempotencyRecordRepository.save(
				new IdempotencyRecord(clientId, idempotencyKey, fingerprint, transfer.getId(), 201, body));
		// Forces the unique-index check now rather than at transaction
		// commit, so a violation surfaces to the caller (TransferOrchestrator)
		// promptly and unambiguously - by this point nothing else in this
		// transaction could plausibly produce the same exception type.
		entityManager.flush();

		return ResponseEntity.created(URI.create("/api/v1/transfers/" + transfer.getId()))
				.contentType(MediaType.APPLICATION_JSON)
				.body(body);
	}

}
