package io.github.anakidkin.aml.domain;

import java.util.Objects;

public record RuleResult(
    String ruleId,
    int ruleVersion,
    RuleStatus status,
    String triggerReason,
    long executionTimeMs,
    boolean isHard
) {
  public RuleResult {
    Objects.requireNonNull(ruleId, "ruleId cannot be null");
    Objects.requireNonNull(status, "status cannot be null");
  }
}