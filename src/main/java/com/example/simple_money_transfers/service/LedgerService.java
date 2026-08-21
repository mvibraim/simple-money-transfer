package com.example.simple_money_transfers.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	@Transactional(readOnly = true)
	public Page<LedgerEntry> getHistory(UUID accountId, Pageable pageable) {
		if (!accountRepository.existsById(accountId)) {
			throw new NotFoundException("Account %s not found".formatted(accountId));
		}
		return ledgerEntryRepository.findByAccountId(accountId, pageable);
	}

}
