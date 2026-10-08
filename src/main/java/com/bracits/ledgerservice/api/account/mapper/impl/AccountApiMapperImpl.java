package com.bracits.ledgerservice.api.account.mapper.impl;

import com.bracits.ledgerservice.api.account.dto.AccountResponse;
import com.bracits.ledgerservice.api.account.dto.BalanceResponse;
import com.bracits.ledgerservice.api.account.dto.CreateAccountRequest;
import com.bracits.ledgerservice.api.account.mapper.AccountApiMapper;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@link AccountApiMapper} implementation.
 */
@Component
public final class AccountApiMapperImpl implements AccountApiMapper {

  private static final long DEFAULT_USER_DATA_64 = 0L;

  @Override
  public Account toAccount(CreateAccountRequest request) {
    return new Account(
        request.accountId(),
        request.code(),
        request.flags() == null ? Set.of() : request.flags(),
        request.userData64() == null ? DEFAULT_USER_DATA_64 : request.userData64());
  }

  @Override
  public AccountResponse toResponse(AccountCreation creation) {
    return new AccountResponse(creation.accountId(), creation.status());
  }

  @Override
  public BalanceResponse toResponse(Balance balance) {
    return new BalanceResponse(
        balance.accountId(),
        balance.debitsPosted(),
        balance.creditsPosted(),
        balance.debitsPending(),
        balance.creditsPending(),
        balance.available());
  }
}
