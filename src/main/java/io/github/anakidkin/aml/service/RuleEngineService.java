package io.github.anakidkin.aml.service;

import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.RuleResult;
import io.github.anakidkin.aml.domain.Transaction;

import java.util.List;

public interface RuleEngineService {

  List<RuleResult> evaluate(Transaction transaction, AccountContext context);
}
