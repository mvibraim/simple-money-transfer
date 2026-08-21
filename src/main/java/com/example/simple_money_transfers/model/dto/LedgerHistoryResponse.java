package com.example.simple_money_transfers.model.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.example.simple_money_transfers.service.LedgerHistoryPage;
import com.example.simple_money_transfers.util.CursorCodec;

public record LedgerHistoryResponse(List<LedgerEntryResponse> entries, int limit,
		@Schema(description = "Opaque cursor for the next page; null once `hasMore` is false",
				example = "djE6NDI") String nextCursor,
		boolean hasMore) {

	public static LedgerHistoryResponse from(LedgerHistoryPage page, int limit) {
		String nextCursor = page.hasMore() ? CursorCodec.encode(page.nextCursorId()) : null;
		return new LedgerHistoryResponse(page.entries().stream().map(LedgerEntryResponse::from).toList(), limit,
				nextCursor, page.hasMore());
	}

}
