package com.example.simple_money_transfers.service;

import java.util.List;

import com.example.simple_money_transfers.model.entity.LedgerEntry;

/**
 * Internal service result for one page of ledger history - raw entry ids, not the wire
 * cursor format. Encoding {@code nextCursorId} into an opaque token is the controller/DTO
 * layer's job.
 */
public record LedgerHistoryPage(List<LedgerEntry> entries, Long nextCursorId, boolean hasMore) {
}
