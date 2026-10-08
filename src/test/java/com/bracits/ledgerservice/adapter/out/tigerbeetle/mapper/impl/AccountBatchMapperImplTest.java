package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.ledgerservice.config.properties.TigerBeetleProperties;
import com.bracits.ledgerservice.domain.account.enums.AccountFlag;
import com.bracits.ledgerservice.domain.account.enums.SystemAccount;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.Balance;
import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.AccountFlags;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.UInt128;
import java.math.BigInteger;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountBatchMapperImplTest {

  private static final int LEDGER = 1;
  private static final UUID SENDER = new UUID(0L, 1001L);

  private final AccountBatchMapperImpl mapper =
      new AccountBatchMapperImpl(
          new TigerBeetleProperties(0L, List.of("127.0.0.1:3000"), Duration.ofMillis(800), LEDGER),
          new TigerBeetleIdMapperImpl());

  @Test
  void accountBatchCarriesAccountFields() {
    Account account =
        new Account(SENDER, 100, Set.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), 77L);

    AccountBatch batch = mapper.toAccountBatch(account);

    assertThat(batch.getLength()).isEqualTo(1);
    batch.beforeFirst();
    assertThat(batch.next()).isTrue();
    assertThat(UInt128.asUUID(batch.getId())).isEqualTo(SENDER);
    assertThat(batch.getLedger()).isEqualTo(LEDGER);
    assertThat(batch.getCode()).isEqualTo(100);
    assertThat(batch.getUserData64()).isEqualTo(77L);
    assertThat(batch.getFlags()).isEqualTo(AccountFlags.DEBITS_MUST_NOT_EXCEED_CREDITS);
  }

  @Test
  void systemAccountBatchHasNoFlags() {
    AccountBatch batch = mapper.toAccountBatch(SystemAccount.EMONEY_ISSUANCE.toAccount());

    batch.beforeFirst();
    assertThat(batch.next()).isTrue();
    assertThat(UInt128.asLong(batch.getId(), UInt128.LeastSignificant)).isEqualTo(900L);
    assertThat(batch.getCode()).isEqualTo(900);
    assertThat(batch.getFlags()).isEqualTo(AccountFlags.NONE);
  }

  @Test
  void flagsMapToTigerBeetleBits() {
    assertThat(mapper.toAccountFlags(Set.of())).isEqualTo(AccountFlags.NONE);
    assertThat(mapper.toAccountFlags(Set.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS)))
        .isEqualTo(AccountFlags.DEBITS_MUST_NOT_EXCEED_CREDITS);
    assertThat(mapper.toAccountFlags(Set.of(AccountFlag.CREDITS_MUST_NOT_EXCEED_DEBITS)))
        .isEqualTo(AccountFlags.CREDITS_MUST_NOT_EXCEED_DEBITS);
    assertThat(mapper.toAccountFlags(Set.of(AccountFlag.HISTORY))).isEqualTo(AccountFlags.HISTORY);
    assertThat(
        mapper.toAccountFlags(
            EnumSet.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS, AccountFlag.HISTORY)))
        .isEqualTo(AccountFlags.DEBITS_MUST_NOT_EXCEED_CREDITS | AccountFlags.HISTORY);
  }

  @Test
  void idBatchHasTheId() {
    IdBatch batch = mapper.toIdBatch(SystemAccount.EMONEY_ISSUANCE.id());

    assertThat(batch.getLength()).isEqualTo(1);
    batch.beforeFirst();
    assertThat(batch.next()).isTrue();
    assertThat(UInt128.asUUID(batch.getId())).isEqualTo(SystemAccount.EMONEY_ISSUANCE.id());
  }

  @Test
  void balanceConvertsBigIntegers() {
    Balance balance =
        mapper.toBalance(
            SENDER,
            BigInteger.valueOf(500),
            BigInteger.valueOf(2_000),
            BigInteger.valueOf(100),
            BigInteger.valueOf(30));

    assertThat(balance).isEqualTo(new Balance(SENDER, 500, 2_000, 100, 30));
    assertThat(balance.available()).isEqualTo(1_400);
  }

  @Test
  void balanceOverflowIsLedgerError() {
    BigInteger tooBig = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

    assertThatThrownBy(
        () -> mapper.toBalance(SENDER, tooBig, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO))
        .isInstanceOf(LedgerErrorException.class);
  }

  @Test
  void balanceFromAccountRow() {
    AccountBatch accounts = new AccountBatch(1);
    accounts.add();
    accounts.setId(UInt128.asBytes(SENDER));

    assertThat(mapper.toBalance(accounts)).contains(new Balance(SENDER, 0, 0, 0, 0));
  }

  @Test
  void noAccountRowIsEmptyBalance() {
    assertThat(mapper.toBalance(new AccountBatch(0))).isEmpty();
  }
}
