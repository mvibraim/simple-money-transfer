package com.example.simple_money_transfers.ledger;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Deliberately exposes no update or delete method - see {@link LedgerEntry}
 * for why immutability matters here.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

	LedgerEntry save(LedgerEntry entry);

	Optional<LedgerEntry> findById(Long id);

}
