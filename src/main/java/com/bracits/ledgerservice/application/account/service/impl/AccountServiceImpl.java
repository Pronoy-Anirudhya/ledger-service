package com.bracits.ledgerservice.application.account.service.impl;

import com.bracits.ledgerservice.application.account.service.AccountService;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;
import com.bracits.ledgerservice.port.out.LedgerStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link AccountService} backed by the {@link LedgerStore} port.
 */
@Service
public final class AccountServiceImpl implements AccountService {

  private final LedgerStore ledgerStore;

  public AccountServiceImpl(LedgerStore ledgerStore) {
    this.ledgerStore = ledgerStore;
  }

  @Override
  public AccountCreation create(Account account) {
    return ledgerStore.createAccount(account);
  }

  @Override
  public Optional<Balance> balance(UUID accountId) {
    return ledgerStore.balance(accountId);
  }
}
