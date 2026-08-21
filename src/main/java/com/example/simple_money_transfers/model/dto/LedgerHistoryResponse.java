package com.example.simple_money_transfers.model.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.example.simple_money_transfers.model.entity.LedgerEntry;

public record LedgerHistoryResponse(List<LedgerEntryResponse> entries, int page, int size, long totalElements,
		int totalPages) {

	public static LedgerHistoryResponse from(Page<LedgerEntry> page) {
		return new LedgerHistoryResponse(page.getContent().stream().map(LedgerEntryResponse::from).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

}
