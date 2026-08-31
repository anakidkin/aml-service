package io.github.anakidkin.aml.service;

import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;

import java.util.List;

public interface OutboxService {

  Transaction save(Transaction transaction, List<RuleResult> ruleResults);
}
