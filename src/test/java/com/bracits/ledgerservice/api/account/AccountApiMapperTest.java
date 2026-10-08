package com.bracits.ledgerservice.api.account;

import static com.bracits.ledgerservice.api.support.ApiFixtures.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.domain.account.Account;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import com.bracits.ledgerservice.domain.account.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.AccountFlag;
import com.bracits.ledgerservice.domain.account.Balance;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountApiMapperTest {

  private final AccountApiMapper mapper = new AccountApiMapper();

  @Test
  void mapsFullRequest() {
    Account account =
        mapper.toAccount(
            new CreateAccountRequest(SENDER, 100, Set.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), 7L));

    assertThat(account)
        .isEqualTo(new Account(SENDER, 100, Set.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), 7L));
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
