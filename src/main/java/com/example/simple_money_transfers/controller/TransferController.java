package com.example.simple_money_transfers.controller;

import java.security.Principal;
import java.util.UUID;

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

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

	private final TransferService transferService;

	private final TransferOrchestrator transferOrchestrator;

	public TransferController(TransferService transferService, TransferOrchestrator transferOrchestrator) {
		this.transferService = transferService;
		this.transferOrchestrator = transferOrchestrator;
	}

	@PostMapping
	public ResponseEntity<String> create(@RequestHeader("Idempotency-Key") String idempotencyKey, Principal principal,
			@Valid @RequestBody CreateTransferRequest request) {
		return transferOrchestrator.transfer(principal.getName(), idempotencyKey, request);
	}

	@GetMapping("/{id}")
	public TransferResponse get(@PathVariable UUID id) {
		return TransferResponse.from(transferService.getTransfer(id));
	}

}
