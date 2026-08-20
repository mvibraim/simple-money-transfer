package com.example.simple_money_transfers.account;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
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

}
