package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.constant.TigerBeetleConstants;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.TigerBeetleIdMapper;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.TransferBatchMapper;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegLookup;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegResult;
import com.bracits.ledgerservice.config.properties.TigerBeetleProperties;
import com.bracits.ledgerservice.domain.posting.factory.TransferIds;
import com.bracits.ledgerservice.domain.posting.model.Leg;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.tigerbeetle.CreateTransferResultBatch;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.TransferBatch;
import com.tigerbeetle.TransferFlags;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link TransferBatchMapper} for the configured ledger, with ids converted by
 * {@link TigerBeetleIdMapper}.
 */
@Component
public final class TransferBatchMapperImpl implements TransferBatchMapper {

  private final int ledger;
  private final TigerBeetleIdMapper idMapper;

  public TransferBatchMapperImpl(TigerBeetleProperties properties, TigerBeetleIdMapper idMapper) {
    this.ledger = properties.ledger();
    this.idMapper = idMapper;
  }

  @Override
  public TransferBatch toTransferBatch(Posting posting) {
    List<Leg> legs = posting.legs();
    int legCount = legs.size();
    byte[] postingId = idMapper.toBytes(posting.postingId());
    TransferBatch batch = new TransferBatch(legCount);

    for (int i = 0; i < legCount; i++) {
      Leg leg = legs.get(i);
      int legIndex = i + 1;

      batch.add();
      batch.setId(idMapper.toBytes(TransferIds.legId(posting.postingId(), legIndex)));
      batch.setDebitAccountId(idMapper.toBytes(leg.debitAccountId()));
      batch.setCreditAccountId(idMapper.toBytes(leg.creditAccountId()));
      batch.setAmount(BigInteger.valueOf(leg.amount()));
      batch.setUserData128(postingId);
      batch.setUserData64(posting.userData64());
      batch.setUserData32(posting.product());
      batch.setTimeout(TigerBeetleConstants.TRANSFER_TIMEOUT_NONE);
      batch.setLedger(ledger);
      batch.setCode(leg.code());
      batch.setFlags(legIndex < legCount ? TransferFlags.LINKED : TransferFlags.NONE);
    }

    return batch;
  }

  @Override
  public IdBatch toLegIdBatch(UUID postingId, int legCount) {
    byte[][] ids = new byte[legCount][];

    for (int n = 1; n <= legCount; n++) {
      ids[n - 1] = idMapper.toBytes(TransferIds.legId(postingId, n));
    }

    return new IdBatch(ids);
  }

  @Override
  public List<LegResult> toLegResults(CreateTransferResultBatch results) {
    List<LegResult> legResults = new ArrayList<>(results.getLength());

    results.beforeFirst();
    while (results.next()) {
      legResults.add(new LegResult(results.getStatus(), results.getTimestamp()));
    }

    return legResults;
  }

  @Override
  public LegLookup toLegLookup(UUID postingId, int legCount, TransferBatch found) {
    Map<UUID, Long> timestampsById = new HashMap<>();

    found.beforeFirst();
    while (found.next()) {
      timestampsById.put(idMapper.toUuid(found.getId()), found.getTimestamp());
    }

    for (int n = 1; n <= legCount; n++) {
      if (!timestampsById.containsKey(TransferIds.legId(postingId, n))) {
        return new LegLookup(legCount, n, 0L);
      }
    }

    return new LegLookup(
        legCount,
        LegLookup.NO_MISSING_LEG,
        timestampsById.get(TransferIds.legId(postingId, legCount)));
  }

  @Override
  public PostingLookup toPostingLookup(UUID postingId, LegLookup legLookup) {
    return legLookup.allFound()
        ? PostingLookup.posted(postingId, legLookup.lastLegTimestamp())
        : PostingLookup.notFound(postingId);
  }
}
