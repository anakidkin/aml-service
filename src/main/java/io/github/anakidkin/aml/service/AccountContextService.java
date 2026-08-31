package io.github.anakidkin.aml.service;

import io.github.anakidkin.aml.domain.AccountContext;
import io.github.anakidkin.aml.domain.Transaction;

public interface AccountContextService {
  AccountContext buildAccountContext(Transaction transaction);
}
