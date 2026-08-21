package com.example.simple_money_transfers.account;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.simple_money_transfers.ledger.LedgerHistoryResponse;
import com.example.simple_money_transfers.ledger.LedgerService;
import com.example.simple_money_transfers.transfer.DepositRequest;
import com.example.simple_money_transfers.transfer.TransferOrchestrator;
import com.example.simple_money_transfers.transfer.WithdrawalRequest;

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

	@PostMapping
	public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
		Account account = accountService.createAccount(request);
		return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
			.body(AccountResponse.from(account));
	}

	@GetMapping("/{id}")
	public AccountResponse get(@PathVariable UUID id) {
		return AccountResponse.from(accountService.getAccount(id));
	}

	@GetMapping("/{id}/balance")
	public BalanceResponse balance(@PathVariable UUID id) {
		return BalanceResponse.from(accountService.getAccount(id));
	}

	@PostMapping("/{id}/deposits")
	public ResponseEntity<String> deposit(@PathVariable UUID id,
			@RequestHeader("Idempotency-Key") String idempotencyKey, Principal principal,
			@Valid @RequestBody DepositRequest request) {
		return transferOrchestrator.deposit(principal.getName(), idempotencyKey, id, request);
	}

	@PostMapping("/{id}/withdrawals")
	public ResponseEntity<String> withdraw(@PathVariable UUID id,
			@RequestHeader("Idempotency-Key") String idempotencyKey, Principal principal,
			@Valid @RequestBody WithdrawalRequest request) {
		return transferOrchestrator.withdraw(principal.getName(), idempotencyKey, id, request);
	}

	@GetMapping("/{id}/entries")
	public LedgerHistoryResponse entries(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		// Ordering is fixed to id DESC (matching F08's idx_ledger_account_history
		// index) and is not client-selectable - only page/size are.
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		return LedgerHistoryResponse.from(ledgerService.getHistory(id, pageable));
	}

}
