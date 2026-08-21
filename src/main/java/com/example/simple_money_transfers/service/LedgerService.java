package com.example.simple_money_transfers.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.simple_money_transfers.exception.NotFoundException;
import com.example.simple_money_transfers.model.entity.LedgerEntry;
import com.example.simple_money_transfers.repository.AccountRepository;
import com.example.simple_money_transfers.repository.LedgerEntryRepository;

@Service
public class LedgerService {

	private final AccountRepository accountRepository;

	private final LedgerEntryRepository ledgerEntryRepository;

	public LedgerService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
		this.accountRepository = accountRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	/**
	 * Fetches {@code limit + 1} rows so the extra row tells us whether another page
	 * exists, without a separate count query - then drops it before returning.
	 */
	@Transactional(readOnly = true)
	public LedgerHistoryPage getHistory(UUID accountId, Long cursorId, int limit) {
		if (!accountRepository.existsById(accountId)) {
			throw new NotFoundException("Account %s not found".formatted(accountId));
		}
		Limit fetchLimit = Limit.of(limit + 1);
		List<LedgerEntry> fetched = (cursorId == null)
				? ledgerEntryRepository.findByAccountIdOrderByIdDesc(accountId, fetchLimit)
				: ledgerEntryRepository.findByAccountIdAndIdLessThanOrderByIdDesc(accountId, cursorId, fetchLimit);

		boolean hasMore = fetched.size() > limit;
		List<LedgerEntry> page = hasMore ? fetched.subList(0, limit) : fetched;
		Long nextCursorId = hasMore ? page.get(page.size() - 1).getId() : null;
		return new LedgerHistoryPage(page, nextCursorId, hasMore);
	}

}
