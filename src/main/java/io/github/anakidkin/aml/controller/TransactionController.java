package io.github.anakidkin.aml.controller;

import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.dto.TransactionRequest;
import io.github.anakidkin.aml.dto.TransactionResponse;
import io.github.anakidkin.aml.mapper.TransactionApiMapper;
import io.github.anakidkin.aml.service.TransactionEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller acting as a driving adapter for evaluating incoming transactions.
 * Exposes endpoints for real-time AML risk scoring and rule assessment.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

  private final TransactionEvaluationService transactionEvaluationService;
  private final TransactionApiMapper mapper;


  /**
   * Evaluates an incoming transaction against active AML rules and historical account context.
   *
   * @param request HTTP request payload containing raw transaction data
   * @return {@link TransactionResponse} containing the assigned status and risk evaluation details
   */
  @PostMapping("/evaluate")
  public ResponseEntity<TransactionResponse> evaluateTransaction(@RequestBody TransactionRequest request) {
    Transaction domainTransaction = mapper.toDomain(request);
    Transaction evaluated = transactionEvaluationService.evaluate(domainTransaction);
    return ResponseEntity.ok(mapper.toResponse(evaluated));
  }
}