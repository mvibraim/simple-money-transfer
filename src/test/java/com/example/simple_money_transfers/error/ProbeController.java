package com.example.simple_money_transfers.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller that exists purely to trigger each exception type {@link
 * ApiExceptionHandler} maps, without depending on any real endpoint.
 */
@RestController
@RequestMapping("/probe")
class ProbeController {

  @PostMapping("/not-found")
  String notFound() {
    throw new NotFoundException("account 123 not found");
  }

  @PostMapping("/business-rule")
  String businessRule() {
    throw new BusinessRuleException("insufficient funds");
  }

  @PostMapping("/validated")
  String validated(@Valid @RequestBody ProbeRequest request) {
    return "ok";
  }

  @PostMapping("/boom")
  String boom() {
    throw new IllegalStateException("something exploded with a secret detail");
  }

  @GetMapping("/type-mismatch/{id}")
  String typeMismatch(@PathVariable UUID id) {
    return "ok";
  }

  record ProbeRequest(@NotBlank String name) {}
}
