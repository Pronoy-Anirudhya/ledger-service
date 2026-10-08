package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.domain.account.enums.SystemAccount;
import com.tigerbeetle.UInt128;
import java.math.BigInteger;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TigerBeetleIdMapperImplTest {

  private static final UUID POSTING_ID = UUID.fromString("0192f5a4-1234-7abc-8def-012345678900");

  private final TigerBeetleIdMapperImpl mapper = new TigerBeetleIdMapperImpl();

  @Test
  void feeIncomeIdIsTigerBeetleId200() {
    byte[] bytes = mapper.toBytes(SystemAccount.FEE_INCOME.id());

    assertThat(UInt128.asLong(bytes, UInt128.LeastSignificant)).isEqualTo(200L);
    assertThat(UInt128.asLong(bytes, UInt128.MostSignificant)).isZero();
    assertThat(UInt128.asBigInteger(bytes)).isEqualTo(BigInteger.valueOf(200));
    assertThat(mapper.toUuid(UInt128.asBytes(200L))).isEqualTo(SystemAccount.FEE_INCOME.id());
    assertThat(SystemAccount.FEE_INCOME.id().toString())
        .isEqualTo("00000000-0000-0000-0000-0000000000c8");
  }

  @Test
  void uuidRoundTrips() {
    assertThat(mapper.toUuid(mapper.toBytes(POSTING_ID))).isEqualTo(POSTING_ID);
  }
}
