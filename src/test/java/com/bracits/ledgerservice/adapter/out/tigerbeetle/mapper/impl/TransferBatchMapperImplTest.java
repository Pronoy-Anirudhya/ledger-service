package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegLookup;
import com.bracits.ledgerservice.config.properties.TigerBeetleProperties;
import com.bracits.ledgerservice.domain.account.enums.SystemAccount;
import com.bracits.ledgerservice.domain.posting.enums.PostingStatus;
import com.bracits.ledgerservice.domain.posting.factory.TransferIds;
import com.bracits.ledgerservice.domain.posting.model.Leg;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.TransferBatch;
import com.tigerbeetle.TransferFlags;
import com.tigerbeetle.UInt128;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferBatchMapperImplTest {

  private static final int LEDGER = 1;
  private static final UUID POSTING_ID = UUID.fromString("0192f5a4-1234-7abc-8def-012345678900");
  private static final UUID SENDER = new UUID(0L, 1001L);
  private static final UUID RECEIVER = new UUID(0L, 1002L);

  private final TransferBatchMapperImpl mapper =
      new TransferBatchMapperImpl(
          new TigerBeetleProperties(0L, List.of("127.0.0.1:3000"), Duration.ofMillis(800), LEDGER),
          new TigerBeetleIdMapperImpl());

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
  void legLookupAllFoundIsPostedWithLastLegTimestamp() {
    TransferBatch found = foundTransfers(new long[]{3, 1, 2}, new long[]{300, 100, 200});

    LegLookup lookup = mapper.toLegLookup(POSTING_ID, 3, found);

    assertThat(lookup.allFound()).isTrue();
    assertThat(lookup.lastLegTimestamp()).isEqualTo(300);
    assertThat(mapper.toPostingLookup(POSTING_ID, lookup).status()).isEqualTo(PostingStatus.POSTED);
    assertThat(mapper.toPostingLookup(POSTING_ID, lookup).ledgerTimestamp()).isEqualTo(300);
  }

  @Test
  void legLookupMissingLegIsNotFound() {
    TransferBatch found = foundTransfers(new long[]{1, 3}, new long[]{100, 300});

    LegLookup lookup = mapper.toLegLookup(POSTING_ID, 3, found);

    assertThat(lookup.allFound()).isFalse();
    assertThat(lookup.firstMissingLeg()).isEqualTo(2);
    assertThat(mapper.toPostingLookup(POSTING_ID, lookup).status())
        .isEqualTo(PostingStatus.NOT_FOUND);
  }

  @Test
  void legLookupNothingFound() {
    LegLookup lookup = mapper.toLegLookup(POSTING_ID, 2, new TransferBatch(0));

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
