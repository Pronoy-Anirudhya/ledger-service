package com.bracits.ledgerservice.adapter.out.tigerbeetle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.ledgerservice.config.TigerBeetleProperties;
import com.bracits.ledgerservice.domain.account.Account;
import com.bracits.ledgerservice.domain.account.AccountFlag;
import com.bracits.ledgerservice.domain.account.Balance;
import com.bracits.ledgerservice.domain.account.SystemAccount;
import com.bracits.ledgerservice.domain.posting.Leg;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingStatus;
import com.bracits.ledgerservice.domain.posting.TransferIds;
import com.bracits.ledgerservice.port.out.LedgerErrorException;
import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.AccountFlags;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.TransferBatch;
import com.tigerbeetle.TransferFlags;
import com.tigerbeetle.UInt128;
import java.math.BigInteger;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TigerBeetleMapperTest {

  private static final int LEDGER = 1;
  private static final UUID POSTING_ID = UUID.fromString("0192f5a4-1234-7abc-8def-012345678900");
  private static final UUID SENDER = new UUID(0L, 1001L);
  private static final UUID RECEIVER = new UUID(0L, 1002L);

  private final TigerBeetleMapper mapper =
      new TigerBeetleMapper(
          new TigerBeetleProperties(0L, List.of("127.0.0.1:3000"), Duration.ofMillis(800), LEDGER));

  @Test
  void transferBatchHasOneRowPerLegWithAllFields() {
    Posting posting =
        new Posting(
            POSTING_ID,
            1,
            42L,
            List.of(
                new Leg(SENDER, RECEIVER, 100_000L, 10),
                new Leg(SENDER, SystemAccount.FEE_INCOME.id(), 348L, 11),
                new Leg(SENDER, SystemAccount.VAT_PAYABLE.id(), 65L, 12),
                new Leg(SENDER, SystemAccount.COMMISSION_PAYABLE.id(), 87L, 13)));

    TransferBatch batch = mapper.toTransferBatch(posting);

    assertThat(batch.getLength()).isEqualTo(4);
    batch.beforeFirst();
    for (int n = 1; n <= 4; n++) {
      assertThat(batch.next()).isTrue();
      Leg leg = posting.legs().get(n - 1);
      assertThat(UInt128.asUUID(batch.getId())).isEqualTo(TransferIds.legId(POSTING_ID, n));
      assertThat(UInt128.asUUID(batch.getId()).getLeastSignificantBits() & 0xFF).isEqualTo(n);
      assertThat(UInt128.asUUID(batch.getDebitAccountId())).isEqualTo(leg.debitAccountId());
      assertThat(UInt128.asUUID(batch.getCreditAccountId())).isEqualTo(leg.creditAccountId());
      assertThat(batch.getAmount()).isEqualTo(BigInteger.valueOf(leg.amount()));
      assertThat(UInt128.asUUID(batch.getUserData128())).isEqualTo(POSTING_ID);
      assertThat(batch.getUserData64()).isEqualTo(42L);
      assertThat(batch.getUserData32()).isEqualTo(1);
      assertThat(batch.getLedger()).isEqualTo(LEDGER);
      assertThat(batch.getCode()).isEqualTo(leg.code());
      assertThat(batch.getTimeout()).isZero();
      assertThat(batch.getPendingId()).isEqualTo(new byte[16]);
      assertThat(batch.getTimestamp()).isZero();
      int expectedFlags = n < 4 ? TransferFlags.LINKED : TransferFlags.NONE;
      assertThat(batch.getFlags()).isEqualTo(expectedFlags);
    }
    assertThat(batch.next()).isFalse();
  }

  @Test
  void singleLegPostingIsNotLinked() {
    Posting posting = new Posting(POSTING_ID, 0, 0L, List.of(new Leg(SENDER, RECEIVER, 1L, 1)));

    TransferBatch batch = mapper.toTransferBatch(posting);

    batch.beforeFirst();
    assertThat(batch.next()).isTrue();
    assertThat(batch.getFlags()).isEqualTo(TransferFlags.NONE);
    assertThat(UInt128.asUUID(batch.getId())).isEqualTo(TransferIds.legId(POSTING_ID, 1));
  }

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
    assertThat(mapper.toAccountFlags(EnumSet.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS, AccountFlag.HISTORY)))
        .isEqualTo(AccountFlags.DEBITS_MUST_NOT_EXCEED_CREDITS | AccountFlags.HISTORY);
  }

  @Test
  void legIdBatchHasLegIdsInOrder() {
    IdBatch batch = mapper.toLegIdBatch(POSTING_ID, 3);

    assertThat(batch.getLength()).isEqualTo(3);
    batch.beforeFirst();
    for (int n = 1; n <= 3; n++) {
      assertThat(batch.next()).isTrue();
      assertThat(UInt128.asUUID(batch.getId())).isEqualTo(TransferIds.legId(POSTING_ID, n));
    }
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

  @Test
  void legLookupAllFoundIsPostedWithLastLegTimestamp() {
    TransferBatch found = foundTransfers(new long[] {3, 1, 2}, new long[] {300, 100, 200});

    TigerBeetleMapper.LegLookup lookup = mapper.toLegLookup(POSTING_ID, 3, found);

    assertThat(lookup.allFound()).isTrue();
    assertThat(lookup.lastLegTimestamp()).isEqualTo(300);
    assertThat(mapper.toPostingLookup(POSTING_ID, lookup).status()).isEqualTo(PostingStatus.POSTED);
    assertThat(mapper.toPostingLookup(POSTING_ID, lookup).ledgerTimestamp()).isEqualTo(300);
  }

  @Test
  void legLookupMissingLegIsNotFound() {
    TransferBatch found = foundTransfers(new long[] {1, 3}, new long[] {100, 300});

    TigerBeetleMapper.LegLookup lookup = mapper.toLegLookup(POSTING_ID, 3, found);

    assertThat(lookup.allFound()).isFalse();
    assertThat(lookup.firstMissingLeg()).isEqualTo(2);
    assertThat(mapper.toPostingLookup(POSTING_ID, lookup).status())
        .isEqualTo(PostingStatus.NOT_FOUND);
  }

  @Test
  void legLookupNothingFound() {
    TigerBeetleMapper.LegLookup lookup = mapper.toLegLookup(POSTING_ID, 2, new TransferBatch(0));

    assertThat(lookup.firstMissingLeg()).isEqualTo(1);
    assertThat(mapper.toPostingLookup(POSTING_ID, lookup).isPosted()).isFalse();
  }

  private static TransferBatch foundTransfers(long[] legs, long[] timestamps) {
    TransferBatch batch = new TransferBatch(legs.length);
    for (int i = 0; i < legs.length; i++) {
      batch.add();
      batch.setId(UInt128.asBytes(TransferIds.legId(POSTING_ID, (int) legs[i])));
      batch.setTimestamp(timestamps[i]);
    }
    return batch;
  }
}
