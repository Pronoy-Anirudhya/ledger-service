package com.bracits.ledgerservice.api.account;

import com.bracits.ledgerservice.domain.account.Account;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import com.bracits.ledgerservice.domain.account.Balance;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Maps account requests to the domain and account results to response bodies. */
@Component
public final class AccountApiMapper {

  private static final long DEFAULT_USER_DATA_64 = 0L;

  /** Request → domain; absent flags mean none, absent {@code userData64} means 0. */
  public Account toAccount(CreateAccountRequest request) {
    return new Account(
        request.accountId(),
        request.code(),
        request.flags() == null ? Set.of() : request.flags(),
        request.userData64() == null ? DEFAULT_USER_DATA_64 : request.userData64());
  }

  /** 201 / 200 body. */
  public AccountResponse toResponse(AccountCreation creation) {
    return new AccountResponse(creation.accountId(), creation.status());
  }

  /** Balance body, with the credit-normal available balance. */
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
