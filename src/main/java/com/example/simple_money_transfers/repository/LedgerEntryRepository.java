package com.example.simple_money_transfers.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import com.example.simple_money_transfers.model.entity.LedgerEntry;

/**
 * Deliberately exposes no update or delete method - see {@link LedgerEntry} for why
 * immutability matters here.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

	LedgerEntry save(LedgerEntry entry);

	Optional<LedgerEntry> findById(Long id);

	Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);

}
