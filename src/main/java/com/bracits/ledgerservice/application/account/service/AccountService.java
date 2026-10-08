package com.bracits.ledgerservice.application.account.service;

import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;
import java.util.Optional;
import java.util.UUID;

/**
 * Account use cases: create an account and read its balance.
 */
public interface AccountService {

  /**
   * Creates the account; an identical existing account is reported as {@code EXISTS}.
   */
  AccountCreation create(Account account);

  /**
   * The account's balances, or empty if it does not exist.
   */
  Optional<Balance> balance(UUID accountId);
}
