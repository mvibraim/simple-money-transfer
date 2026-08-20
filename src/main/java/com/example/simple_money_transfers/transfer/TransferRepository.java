package com.example.simple_money_transfers.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately minimal: transfers are an append-only audit record, so this exposes no update or
 * delete method - only what {@code save} and read access actually require.
 */
public interface TransferRepository extends Repository<Transfer, UUID> {

  Transfer save(Transfer transfer);

  Optional<Transfer> findById(UUID id);
}
