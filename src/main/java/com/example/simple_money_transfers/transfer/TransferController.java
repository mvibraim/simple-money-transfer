package com.example.simple_money_transfers.transfer;

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
@RequestMapping("/api/v1/transfers")
public class TransferController {

	private final TransferService transferService;

	public TransferController(TransferService transferService) {
		this.transferService = transferService;
	}

	@PostMapping
	public ResponseEntity<TransferResponse> create(@Valid @RequestBody CreateTransferRequest request) {
		Transfer transfer = transferService.execute(new TransferCommand(
				request.sourceAccountId(), request.targetAccountId(), request.amount(), request.currency(),
				TransferKind.TRANSFER, request.reference()));
		return ResponseEntity.created(URI.create("/api/v1/transfers/" + transfer.getId()))
				.body(TransferResponse.from(transfer));
	}

	@GetMapping("/{id}")
	public TransferResponse get(@PathVariable UUID id) {
		return TransferResponse.from(transferService.getTransfer(id));
	}

}
