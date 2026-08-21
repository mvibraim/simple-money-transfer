package com.example.simple_money_transfers.service;

import java.security.SecureRandom;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.simple_money_transfers.exception.NotFoundException;
import com.example.simple_money_transfers.model.dto.CreateAccountRequest;
import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountStatus;
import com.example.simple_money_transfers.model.entity.AccountType;
import com.example.simple_money_transfers.repository.AccountRepository;
import com.example.simple_money_transfers.util.MoneyNormalizer;

@Service
public class AccountService {

	private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AccountRepository accountRepository;

	public AccountService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Transactional
	public Account createAccount(CreateAccountRequest request) {
		MoneyNormalizer.requireValidCurrency(request.currency());
		Account account = new Account(generateAccountRef(), request.holderName(), AccountType.CUSTOMER,
				request.currency(), AccountStatus.ACTIVE);
		return accountRepository.save(account);
	}

	@Transactional(readOnly = true)
	public Account getAccount(UUID id) {
		return accountRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Account %s not found".formatted(id)));
	}

	private static String generateAccountRef() {
		StringBuilder ref = new StringBuilder("ACCT-");
		for (int i = 0; i < 12; i++) {
			ref.append(REF_ALPHABET.charAt(RANDOM.nextInt(REF_ALPHABET.length())));
		}
		return ref.toString();
	}

}
