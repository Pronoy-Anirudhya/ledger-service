package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.tigerbeetle.CreateAccountStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccountResultMapperImplTest {

  private final AccountResultMapperImpl mapper = new AccountResultMapperImpl();

  @Test
  void accountCreated() {
    assertThat(mapper.mapAccount(CreateAccountStatus.Created))
        .isEqualTo(AccountCreationStatus.CREATED);
    assertThat(mapper.mapAccount(List.of(CreateAccountStatus.Created)))
        .isEqualTo(AccountCreationStatus.CREATED);
  }

  @Test
  void accountExists() {
    assertThat(mapper.mapAccount(CreateAccountStatus.Exists))
        .isEqualTo(AccountCreationStatus.EXISTS);
  }

  @ParameterizedTest
  @EnumSource(
      value = CreateAccountStatus.class,
      names = "ExistsWithDifferent.*",
      mode = EnumSource.Mode.MATCH_ALL)
  void accountExistsWithDifferentIsConflict(CreateAccountStatus status) {
    assertThat(mapper.mapAccount(status)).isEqualTo(AccountCreationStatus.CONFLICT);
  }

  @ParameterizedTest
  @EnumSource(
      value = CreateAccountStatus.class,
      names = {"Created", "Exists", "ExistsWithDifferent.*"},
      mode = EnumSource.Mode.MATCH_NONE)
  void otherAccountStatusThrows(CreateAccountStatus status) {
    assertThatThrownBy(() -> mapper.mapAccount(status)).isInstanceOf(LedgerErrorException.class);
  }

  @Test
  void accountResultCountMismatchThrows() {
    assertThatThrownBy(() -> mapper.mapAccount(List.<CreateAccountStatus>of()))
        .isInstanceOf(LedgerErrorException.class);
    assertThatThrownBy(
        () -> mapper.mapAccount(List.of(CreateAccountStatus.Created, CreateAccountStatus.Created)))
        .isInstanceOf(LedgerErrorException.class);
  }
}
