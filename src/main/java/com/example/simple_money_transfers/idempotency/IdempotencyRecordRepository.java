package com.example.simple_money_transfers.idempotency;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Deliberately exposes no update or delete method - see {@link IdempotencyRecord} for why
 * immutability matters here. Records are never reaped: a TTL would be a live double-spend window
 * (see F14).
 */
public interface IdempotencyRecordRepository extends Repository<IdempotencyRecord, Long> {

  IdempotencyRecord save(IdempotencyRecord record);

  Optional<IdempotencyRecord> findByClientIdAndIdempotencyKey(
      String clientId, String idempotencyKey);
}
