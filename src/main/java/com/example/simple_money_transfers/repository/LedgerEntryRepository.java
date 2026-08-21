package com.example.simple_money_transfers.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;

import com.example.simple_money_transfers.model.entity.LedgerEntry;

/**
 * Deliberately exposes no update or delete method - see {@link LedgerEntry} for why
 * immutability matters here.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

	LedgerEntry save(LedgerEntry entry);

	Optional<LedgerEntry> findById(Long id);

	List<LedgerEntry> findByAccountIdOrderByIdDesc(UUID accountId, Limit limit);

	List<LedgerEntry> findByAccountIdAndIdLessThanOrderByIdDesc(UUID accountId, Long cursorId, Limit limit);

}
