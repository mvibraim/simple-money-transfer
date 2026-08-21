package com.example.simple_money_transfers.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.simple_money_transfers.model.dto.CreateTransferRequest;
import com.example.simple_money_transfers.model.dto.TransferResponse;
import com.example.simple_money_transfers.service.TransferOrchestrator;
import com.example.simple_money_transfers.service.TransferService;

@Tag(name = "Transfers", description = "Money movement between two accounts.")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

	private final TransferService transferService;

	private final TransferOrchestrator transferOrchestrator;

	public TransferController(TransferService transferService, TransferOrchestrator transferOrchestrator) {
		this.transferService = transferService;
		this.transferOrchestrator = transferOrchestrator;
	}

	@Operation(summary = "Transfer funds between two accounts",
			description = "Requires an `Idempotency-Key`: a replayed key with an identical body re-serves the "
					+ "original 201 response; the same key with a different body is rejected with 422.")
	@ApiResponses({
			@ApiResponse(responseCode = "201",
					description = "Transfer applied (or replayed from an earlier identical request)",
					headers = @Header(name = "Location", description = "URL of the resulting transfer",
							schema = @Schema(type = "string")),
					content = @Content(schema = @Schema(implementation = TransferResponse.class))),
			@ApiResponse(responseCode = "400",
					description = "Validation failed, malformed body, or missing `Idempotency-Key`",
					content = @Content),
			@ApiResponse(responseCode = "404", description = "Unknown source or target account id", content = @Content),
			@ApiResponse(responseCode = "422",
					description = "Insufficient funds, mismatched currencies, source equals target, an inactive "
							+ "account, or the same `Idempotency-Key` reused with a different body",
					content = @Content),
			@ApiResponse(responseCode = "503", description = "Temporarily unable to acquire the account locks; retry",
					content = @Content) })
	@PostMapping
	public ResponseEntity<String> create(
			@Parameter(description = "Client-chosen key scoping this request for safe retries, unique per API client",
					required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
			Principal principal, @Valid @RequestBody CreateTransferRequest request) {
		return transferOrchestrator.transfer(principal.getName(), idempotencyKey, request);
	}

	@Operation(summary = "Fetch a transfer by id")
	@ApiResponses({ @ApiResponse(responseCode = "200", description = "Transfer found"),
			@ApiResponse(responseCode = "404", description = "No transfer with this id", content = @Content) })
	@GetMapping("/{id}")
	public TransferResponse get(@PathVariable UUID id) {
		return TransferResponse.from(transferService.getTransfer(id));
	}

}
