package com.bracits.ledgerservice.api.account.mapper;

import com.bracits.ledgerservice.api.account.dto.AccountResponse;
import com.bracits.ledgerservice.api.account.dto.BalanceResponse;
import com.bracits.ledgerservice.api.account.dto.CreateAccountRequest;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;

/**
 * Maps account requests to the domain and account results to response bodies.
 */
public interface AccountApiMapper {

  /**
   * Request → domain; absent flags mean none, absent {@code userData64} means 0.
   */
  Account toAccount(CreateAccountRequest request);

  /**
   * 201 / 200 body.
   */
  AccountResponse toResponse(AccountCreation creation);

  /**
   * Balance body, with the credit-normal available balance.
   */
  BalanceResponse toResponse(Balance balance);
}
