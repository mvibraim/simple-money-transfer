package com.example.simple_money_transfers.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountStatus;
import com.example.simple_money_transfers.model.entity.AccountType;
import com.example.simple_money_transfers.model.entity.IdempotencyRecord;
import com.example.simple_money_transfers.model.entity.Transfer;
import com.example.simple_money_transfers.service.TransferService;
import com.example.simple_money_transfers.support.AbstractIntegrationTest;

class IdempotencyRecordRepositoryIT extends AbstractIntegrationTest {

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransferService transferService;

	@Autowired
	private IdempotencyRecordRepository idempotencyRecordRepository;

	private Transfer aRealTransfer() {
		Account source = accountRepository
			.save(new Account("IS1", "Source", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		Account target = accountRepository
			.save(new Account("IT1", "Target", AccountType.CUSTOMER, "USD", AccountStatus.ACTIVE));
		transferService.deposit(source.getId(), new BigDecimal("50.00"), null);
		return transferService.execute(new com.example.simple_money_transfers.model.dto.TransferCommand(source.getId(),
				target.getId(), new BigDecimal("10.00"), "USD",
				com.example.simple_money_transfers.model.entity.TransferKind.TRANSFER, null));
	}

	@Test
	void savesAndFindsByClientIdAndIdempotencyKey() {
		Transfer transfer = aRealTransfer();

		idempotencyRecordRepository
			.save(new IdempotencyRecord("client-a", "key-1", "a".repeat(64), transfer.getId(), 201, "{}"));

		IdempotencyRecord found = idempotencyRecordRepository.findByClientIdAndIdempotencyKey("client-a", "key-1")
			.orElseThrow();
		assertThat(found.getTransferId()).isEqualTo(transfer.getId());
		assertThat(found.getResponseStatus()).isEqualTo(201);
	}

	@Test
	void sameKeyIsUniquePerClientButNotAcrossClients() {
		Transfer transfer = aRealTransfer();

		idempotencyRecordRepository
			.save(new IdempotencyRecord("client-a", "shared-key", "a".repeat(64), transfer.getId(), 201, "{}"));
		// a different client using the identical key string is fine
		idempotencyRecordRepository
			.save(new IdempotencyRecord("client-b", "shared-key", "b".repeat(64), transfer.getId(), 201, "{}"));

		assertThatThrownBy(() -> idempotencyRecordRepository
			.save(new IdempotencyRecord("client-a", "shared-key", "c".repeat(64), transfer.getId(), 201, "{}")))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void repositoryExposesNoUpdateOrDeleteMethod() {
		var methodNames = java.util.Arrays.stream(IdempotencyRecordRepository.class.getMethods())
			.map(java.lang.reflect.Method::getName)
			.toList();
		assertThat(methodNames).containsExactlyInAnyOrder("save", "findByClientIdAndIdempotencyKey");
	}

}
