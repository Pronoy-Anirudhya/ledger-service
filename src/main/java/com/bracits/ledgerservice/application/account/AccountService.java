package com.bracits.ledgerservice.application.account;

import com.bracits.ledgerservice.domain.account.Account;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import com.bracits.ledgerservice.domain.account.Balance;
import com.bracits.ledgerservice.port.out.LedgerStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Account use cases: create an account and read its balance. */
@Service
public final class AccountService {

  private final LedgerStore ledgerStore;

  public AccountService(LedgerStore ledgerStore) {
    this.ledgerStore = ledgerStore;
  }

  /** Creates the account; an identical existing account is reported as {@code EXISTS}. */
  public AccountCreation create(Account account) {
    return ledgerStore.createAccount(account);
  }

  /** The account's balances, or empty if it does not exist. */
  public Optional<Balance> balance(UUID accountId) {
    return ledgerStore.balance(accountId);
  }
}
