package com.example.simple_money_transfers.transfer;

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

@Entity
@Table(name = "transfer")
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "source_account_id", nullable = false, updatable = false)
	private UUID sourceAccountId;

	@Column(name = "target_account_id", nullable = false, updatable = false)
	private UUID targetAccountId;

	@Column(nullable = false, updatable = false)
	private BigDecimal amount;

	@Column(nullable = false, updatable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private TransferKind kind;

	@Column(updatable = false, length = 140)
	private String reference;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Transfer() {
		// JPA
	}

	public Transfer(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String currency,
			TransferKind kind, String reference) {
		this.sourceAccountId = sourceAccountId;
		this.targetAccountId = targetAccountId;
		this.amount = amount;
		this.currency = currency;
		this.kind = kind;
		this.reference = reference;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSourceAccountId() {
		return sourceAccountId;
	}

	public UUID getTargetAccountId() {
		return targetAccountId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public TransferKind getKind() {
		return kind;
	}

	public String getReference() {
		return reference;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
