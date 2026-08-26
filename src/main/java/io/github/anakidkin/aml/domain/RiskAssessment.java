package io.github.anakidkin.aml.domain;

import java.util.List;
import java.util.Objects;

public record RiskAssessment(
    double score,
    RiskLevel level,
    List<RuleResult> ruleResults
) {
  public RiskAssessment {
    Objects.requireNonNull(level, "level cannot be null");
    ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);

    if (score < 0.0 || score > 100.0) {
      throw new IllegalArgumentException("Risk score must be between 0.0 and 100.0");
    }
  }
}

