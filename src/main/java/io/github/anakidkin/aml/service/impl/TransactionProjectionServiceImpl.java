package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.RuleStatus;
import io.github.anakidkin.aml.dto.TransactionEvaluatedEvent;
import io.github.anakidkin.aml.entity.AccountTransactionHistoryEntity;
import io.github.anakidkin.aml.entity.AuditLogEntity;
import io.github.anakidkin.aml.repository.CassandraAuditRepository;
import io.github.anakidkin.aml.repository.CassandraHistoryRepository;
import io.github.anakidkin.aml.service.TransactionProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProjectionServiceImpl implements TransactionProjectionService {

  private final CassandraHistoryRepository cassandraHistoryRepository;
  private final CassandraAuditRepository cassandraAuditRepository;

  @Override
  public void projectTransactionEvaluation(TransactionEvaluatedEvent event) {
    log.debug("Starting projection process for transactionId={}", event.transactionId());
    saveAccountHistory(event);
    saveAuditLogs(event);
    log.debug("Successfully finished projection process for transactionId={}", event.transactionId());
  }


  private void saveAccountHistory(TransactionEvaluatedEvent event) {
    var historyEntity = AccountTransactionHistoryEntity.builder()
        .accountFrom(event.accountFrom())
        .createdAt(event.createdAt())
        .transactionId(event.transactionId())
        .accountTo(event.accountTo())
        .amount(event.amount())
        .currency(event.currency())
        .mccCode(event.mccCode())
        .isP2p(event.isP2p())
        .build();
    cassandraHistoryRepository.save(historyEntity);
    log.trace("Account history saved for tx={}", event.transactionId());
  }

  private void saveAuditLogs(TransactionEvaluatedEvent event) {
    if (event.ruleResults() == null || event.ruleResults().isEmpty()) {
      return;
    }

    List<AuditLogEntity> auditLogs = event.ruleResults().stream()
        .map(rule -> AuditLogEntity.builder()
            .transactionId(event.transactionId())
            .createdAt(event.evaluatedAt() != null ? event.evaluatedAt() : event.createdAt())
            .ruleId(rule.ruleId())
            .ruleVersion(rule.ruleVersion())
            .status(rule.status().name())
            .triggerReason(rule.triggerReason())
            .executionTimeMs(rule.executionTimeMs())
            .isHard(rule.isHard())
            .riskScore(calculateRiskScore(rule))
            .build())
        .toList();

    cassandraAuditRepository.saveAll(auditLogs);
    log.trace("Audit logs saved for tx={}", event.transactionId());
  }

  /**
   * Calculates the risk score for an individual rule execution result.
   */
  private Double calculateRiskScore(RuleResult ruleResult) {
    if (ruleResult.status() == RuleStatus.PASSED) {
      return 0.0;
    }
    return ruleResult.isHard() ? 100.0 : 50.0;
  }
}
