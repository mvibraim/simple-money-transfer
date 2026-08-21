package com.example.simple_money_transfers.model.dto;

import java.util.List;

import com.example.simple_money_transfers.service.LedgerHistoryPage;
import com.example.simple_money_transfers.util.CursorCodec;

public record LedgerHistoryResponse(List<LedgerEntryResponse> entries, int limit, String nextCursor, boolean hasMore) {

	public static LedgerHistoryResponse from(LedgerHistoryPage page, int limit) {
		String nextCursor = page.hasMore() ? CursorCodec.encode(page.nextCursorId()) : null;
		return new LedgerHistoryResponse(page.entries().stream().map(LedgerEntryResponse::from).toList(), limit,
				nextCursor, page.hasMore());
	}

}
