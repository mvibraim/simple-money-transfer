package com.example.simple_money_transfers.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	/**
	 * Locks every requested account in a single query, ordered ascending
	 * by id. This ordering is the entire deadlock-avoidance mechanism for
	 * the transfer write path (F09): two opposing concurrent transfers
	 * that each locked "source, then target" separately could deadlock,
	 * but because every caller acquires locks through this one ordered
	 * query, one always wins the race and the other simply waits. No
	 * other code path may lock account rows outside this method.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id in :ids order by a.id")
	List<Account> lockAllById(@Param("ids") Collection<UUID> ids);

	Optional<Account> findByAccountTypeAndCurrency(AccountType accountType, String currency);

}
