package com.example.simple_money_transfers.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "account")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "account_ref", nullable = false, unique = true)
	private String accountRef;

	@Column(name = "holder_name", nullable = false)
	private String holderName;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false, updatable = false)
	private AccountType accountType;

	// Immutable after creation: the transfer write path's currency-match
	// check (F09) is race-free only because currency never changes
	// underneath a locked row.
	@Column(nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(nullable = false)
	private BigDecimal balance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Version
	private Long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Account() {
		// JPA
	}

	public Account(String accountRef, String holderName, AccountType accountType, String currency,
			AccountStatus status) {
		this.accountRef = accountRef;
		this.holderName = holderName;
		this.accountType = accountType;
		this.currency = currency;
		this.balance = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public String getAccountRef() {
		return accountRef;
	}

	public String getHolderName() {
		return holderName;
	}

	public AccountType getAccountType() {
		return accountType;
	}

	public String getCurrency() {
		return currency;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
