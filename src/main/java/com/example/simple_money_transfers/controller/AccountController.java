package com.example.simple_money_transfers.controller;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.simple_money_transfers.model.dto.AccountResponse;
import com.example.simple_money_transfers.model.dto.BalanceResponse;
import com.example.simple_money_transfers.model.dto.CreateAccountRequest;
import com.example.simple_money_transfers.model.dto.DepositRequest;
import com.example.simple_money_transfers.model.dto.LedgerHistoryResponse;
import com.example.simple_money_transfers.model.dto.TransferResponse;
import com.example.simple_money_transfers.model.dto.WithdrawalRequest;
import com.example.simple_money_transfers.model.entity.Account;
import com.example.simple_money_transfers.service.AccountService;
import com.example.simple_money_transfers.service.LedgerService;
import com.example.simple_money_transfers.service.TransferOrchestrator;
import com.example.simple_money_transfers.util.CursorCodec;

@Tag(name = "Accounts",
		description = "Account lifecycle, balance lookup, funding (deposits/withdrawals), and ledger history.")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final AccountService accountService;

	private final TransferOrchestrator transferOrchestrator;

	private final LedgerService ledgerService;

	public AccountController(AccountService accountService, TransferOrchestrator transferOrchestrator,
			LedgerService ledgerService) {
		this.accountService = accountService;
		this.transferOrchestrator = transferOrchestrator;
		this.ledgerService = ledgerService;
	}

	@Operation(summary = "Create an account")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Account created",
					headers = @Header(name = "Location", description = "URL of the new account",
							schema = @Schema(type = "string"))),
			@ApiResponse(responseCode = "400", description = "Validation failed or malformed request body",
					content = @Content) })
	@PostMapping
	public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
		Account account = accountService.createAccount(request);
		return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
			.body(AccountResponse.from(account));
	}

	@Operation(summary = "Fetch an account by id")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Account found"),
			@ApiResponse(responseCode = "404", description = "No account with this id", content = @Content) })
	@GetMapping("/{id}")
	public AccountResponse get(@PathVariable UUID id) {
		return AccountResponse.from(accountService.getAccount(id));
	}

	@Operation(summary = "Fetch an account's current balance")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Balance as of now"),
			@ApiResponse(responseCode = "404", description = "No account with this id", content = @Content) })
	@GetMapping("/{id}/balance")
	public BalanceResponse balance(@PathVariable UUID id) {
		return BalanceResponse.from(accountService.getAccount(id));
	}

	@Operation(summary = "Deposit funds into an account",
			description = "Funded from the SYSTEM account for the account's currency. Requires an `Idempotency-Key`: "
					+ "a replayed key with an identical body re-serves the original 201 response; the same key with a "
					+ "different body is rejected with 422.")
	@ApiResponses({
			@ApiResponse(responseCode = "201",
					description = "Deposit applied (or replayed from an earlier identical request)",
					headers = @Header(name = "Location", description = "URL of the resulting transfer",
							schema = @Schema(type = "string")),
					content = @Content(schema = @Schema(implementation = TransferResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Validation failed, malformed body, or missing `Idempotency-Key`",
					content = @Content),
			@ApiResponse(responseCode = "404", description = "No account with this id", content = @Content),
			@ApiResponse(responseCode = "422",
					description = "Same `Idempotency-Key` reused with a different body, the account is not ACTIVE, "
							+ "or its currency has no funding SYSTEM account",
					content = @Content),
			@ApiResponse(responseCode = "503", description = "Temporarily unable to acquire the account lock; retry",
					content = @Content) })
	@PostMapping("/{id}/deposits")
	public ResponseEntity<String> deposit(@PathVariable UUID id,
			@Parameter(description = "Client-chosen key scoping this request for safe retries, unique per API client",
					required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
			Principal principal, @Valid @RequestBody DepositRequest request) {
		return transferOrchestrator.deposit(principal.getName(), idempotencyKey, id, request);
	}

	@Operation(summary = "Withdraw funds from an account",
			description = "Sent to the SYSTEM account for the account's currency. Requires an `Idempotency-Key`; "
					+ "see the deposit endpoint for its replay semantics.")
	@ApiResponses({
			@ApiResponse(responseCode = "201",
					description = "Withdrawal applied (or replayed from an earlier identical request)",
					headers = @Header(name = "Location", description = "URL of the resulting transfer",
							schema = @Schema(type = "string")),
					content = @Content(schema = @Schema(implementation = TransferResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Validation failed, malformed body, or missing `Idempotency-Key`",
					content = @Content),
			@ApiResponse(responseCode = "404", description = "No account with this id", content = @Content),
			@ApiResponse(responseCode = "422",
					description = "Insufficient funds, same `Idempotency-Key` reused with a different body, or the "
							+ "account is not ACTIVE",
					content = @Content),
			@ApiResponse(responseCode = "503", description = "Temporarily unable to acquire the account lock; retry",
					content = @Content) })
	@PostMapping("/{id}/withdrawals")
	public ResponseEntity<String> withdraw(@PathVariable UUID id,
			@Parameter(description = "Client-chosen key scoping this request for safe retries, unique per API client",
					required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
			Principal principal, @Valid @RequestBody WithdrawalRequest request) {
		return transferOrchestrator.withdraw(principal.getName(), idempotencyKey, id, request);
	}

	@Operation(summary = "List an account's ledger entries, newest first",
			description = "Cursor-paginated. Pass the previous response's `nextCursor` to fetch the next page; "
					+ "`hasMore` is false once the last page has been reached.")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "A page of ledger entries"),
			@ApiResponse(responseCode = "400", description = "Malformed cursor, or `limit` outside 1-100",
					content = @Content),
			@ApiResponse(responseCode = "404", description = "No account with this id", content = @Content) })
	@GetMapping("/{id}/entries")
	public LedgerHistoryResponse entries(@PathVariable UUID id, @Parameter(
			description = "Opaque pagination cursor from a previous response's `nextCursor`; omit for the first page") @RequestParam(
					required = false) String cursor,
			@Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		// Ordering is fixed to id DESC (matching F08's idx_ledger_account_history
		// index) and is not client-selectable - only cursor/limit are.
		Long cursorId = (cursor != null) ? CursorCodec.decode(cursor) : null;
		return LedgerHistoryResponse.from(ledgerService.getHistory(id, cursorId, limit), limit);
	}

}
