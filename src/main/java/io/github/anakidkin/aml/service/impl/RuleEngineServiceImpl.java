package io.github.anakidkin.aml.service.impl;

import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;
import io.github.anakidkin.aml.rules.AmlRule;
import io.github.anakidkin.aml.service.RuleEngineService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RuleEngineServiceImpl implements RuleEngineService {

  private final List<AmlRule> amlRules;

  public RuleEngineServiceImpl(List<AmlRule> amlRules) {
    this.amlRules = amlRules.stream()
        .sorted(Comparator.comparingInt(AmlRule::getPriority))
        .toList();
  }


  @Override
  public List<RuleResult> evaluate(Transaction transaction, AccountContext context) {
    return amlRules.stream()
        .map(rule -> rule.evaluate(transaction, context))
        .toList();
  }
}
