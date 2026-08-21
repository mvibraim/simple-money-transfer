package com.example.simple_money_transfers.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Immutable by construction, not just by database trigger: no field here has a setter,
 * and {@link LedgerEntryRepository} exposes no update or delete method. This is the
 * application-level half of the immutability guarantee - the Postgres-only trigger (V3)
 * is the other half, and neither alone is sufficient.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "transfer_id", nullable = false, updatable = false)
	private UUID transferId;

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Direction direction;

	// Signed: DEBIT negative, CREDIT positive - SUM(amount) across the
	// whole table is the single invariant proving no money was created or
	// destroyed.
	@Column(nullable = false, updatable = false)
	private BigDecimal amount;

	@Column(nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "balance_after", nullable = false, updatable = false)
	private BigDecimal balanceAfter;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected LedgerEntry() {
		// JPA
	}

	public LedgerEntry(UUID transferId, UUID accountId, Direction direction, BigDecimal amount, String currency,
			BigDecimal balanceAfter) {
		this.transferId = transferId;
		this.accountId = accountId;
		this.direction = direction;
		this.amount = amount;
		this.currency = currency;
		this.balanceAfter = balanceAfter;
	}

	public Long getId() {
		return id;
	}

	public UUID getTransferId() {
		return transferId;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public Direction getDirection() {
		return direction;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
