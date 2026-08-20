package com.example.simple_money_transfers.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Stores only successful outcomes - {@code transfer_id} is {@code NOT NULL} at the database level,
 * so a record without a transfer would be a contradiction. Immutable by construction: no setters,
 * and {@link IdempotencyRecordRepository} exposes no update or delete method.
 */
@Entity
@Table(
    name = "idempotency_record",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "idempotency_key"}))
public class IdempotencyRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "client_id", nullable = false, updatable = false, length = 64)
  private String clientId;

  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255)
  private String idempotencyKey;

  @Column(nullable = false, updatable = false, length = 64)
  private String fingerprint;

  @Column(name = "transfer_id", nullable = false, updatable = false)
  private UUID transferId;

  @Column(name = "response_status", nullable = false, updatable = false)
  private int responseStatus;

  @Column(name = "response_body", nullable = false, updatable = false)
  private String responseBody;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected IdempotencyRecord() {
    // JPA
  }

  public IdempotencyRecord(
      String clientId,
      String idempotencyKey,
      String fingerprint,
      UUID transferId,
      int responseStatus,
      String responseBody) {
    this.clientId = clientId;
    this.idempotencyKey = idempotencyKey;
    this.fingerprint = fingerprint;
    this.transferId = transferId;
    this.responseStatus = responseStatus;
    this.responseBody = responseBody;
  }

  public Long getId() {
    return id;
  }

  public String getClientId() {
    return clientId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public UUID getTransferId() {
    return transferId;
  }

  public int getResponseStatus() {
    return responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
