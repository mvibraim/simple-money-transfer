package com.example.simple_money_transfers.ledger;

import java.util.List;
import org.springframework.data.domain.Page;

public record LedgerHistoryResponse(
    List<LedgerEntryResponse> entries, int page, int size, long totalElements, int totalPages) {

  public static LedgerHistoryResponse from(Page<LedgerEntry> page) {
    return new LedgerHistoryResponse(
        page.getContent().stream().map(LedgerEntryResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
