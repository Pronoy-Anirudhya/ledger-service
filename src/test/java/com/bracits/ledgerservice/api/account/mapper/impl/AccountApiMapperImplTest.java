package com.bracits.ledgerservice.api.account.mapper.impl;

import static com.bracits.ledgerservice.api.support.ApiFixtures.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.api.account.dto.AccountResponse;
import com.bracits.ledgerservice.api.account.dto.BalanceResponse;
import com.bracits.ledgerservice.api.account.dto.CreateAccountRequest;
import com.bracits.ledgerservice.api.account.mapper.AccountApiMapper;
import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.enums.AccountFlag;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountApiMapperImplTest {

  private final AccountApiMapper mapper = new AccountApiMapperImpl();

  @Test
  void mapsFullRequest() {
    Account account =
        mapper.toAccount(
            new CreateAccountRequest(SENDER, 100,
                Set.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), 7L));

    assertThat(account)
        .isEqualTo(
            new Account(SENDER, 100, Set.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), 7L));
  }

  @Test
  void nullFlagsAndUserDataDefault() {
    Account account = mapper.toAccount(new CreateAccountRequest(SENDER, 100, null, null));

    assertThat(account.flags()).isEmpty();
    assertThat(account.userData64()).isZero();
  }

  @Test
  void mapsCreation() {
    assertThat(mapper.toResponse(new AccountCreation(SENDER, AccountCreationStatus.EXISTS)))
        .isEqualTo(new AccountResponse(SENDER, AccountCreationStatus.EXISTS));
  }

  @Test
  void mapsBalanceWithAvailable() {
    BalanceResponse response = mapper.toResponse(new Balance(SENDER, 300, 1_000, 50, 20));

    assertThat(response).isEqualTo(new BalanceResponse(SENDER, 300, 1_000, 50, 20, 650));
  }
}
