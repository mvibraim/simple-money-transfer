package com.example.simple_money_transfers.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.model.entity.AccountType;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	/**
	 * Locks every requested account in a single query, ordered ascending by id. This
	 * ordering is the entire deadlock-avoidance mechanism for the transfer write path
	 * (F09): two opposing concurrent transfers that each locked "source, then target"
	 * separately could deadlock, but because every caller acquires locks through this one
	 * ordered query, one always wins the race and the other simply waits. No other code
	 * path may lock account rows outside this method - and no caller may load an account
	 * into the persistence context beforehand, either (see {@link #findCurrencyById} and
	 * {@link #findIdByAccountTypeAndCurrency}), or this call becomes a lock *upgrade*
	 * instead of a fresh locked load.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id in :ids order by a.id")
	List<Account> lockAllById(@Param("ids") Collection<UUID> ids);

	/**
	 * Scalar projections, not entity loads: deposit/withdraw (F11) must resolve an
	 * account's currency and the SYSTEM account's id without pulling either
	 * {@link Account} into the persistence context, or {@link #lockAllById} - called
	 * moments later, in the same transaction - would find them already managed and take
	 * Hibernate's lock-upgrade path instead of a fresh locked load. That upgrade path
	 * re-checks {@code version} against the possibly-stale in-memory value and throws
	 * {@code ObjectOptimisticLockingFailureException} on a mismatch - harmless for a
	 * single customer account, but the per-currency SYSTEM account is touched by every
	 * deposit and withdrawal in that currency, so any two that merely overlap in time
	 * would fail one of them with a 5xx instead of letting the pessimistic lock serialize
	 * them.
	 */
	@Query("select a.currency from Account a where a.id = :id")
	Optional<String> findCurrencyById(@Param("id") UUID id);

	@Query("select a.id from Account a where a.accountType = :accountType and a.currency = :currency")
	Optional<UUID> findIdByAccountTypeAndCurrency(@Param("accountType") AccountType accountType,
			@Param("currency") String currency);

}
