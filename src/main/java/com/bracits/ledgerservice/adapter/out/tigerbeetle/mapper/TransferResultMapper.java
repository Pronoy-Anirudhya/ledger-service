package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.ChainResult;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegLookup;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegResult;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.util.List;

/**
 * Maps TigerBeetle 0.17 transfer result statuses to posting outcomes (spec 8.2, D7–D11). Pure logic
 * over plain values, so it does not depend on TigerBeetle result batches.
 */
public interface TransferResultMapper {

  /**
   * Maps the per-leg results of one linked chain.
   *
   * @param results  one result per leg, in leg order
   * @param legCount number of legs sent
   */
  ChainResult mapTransfers(List<LegResult> results, int legCount);

  /**
   * Maps the lookup that verifies a partial-exists chain (D8): every leg found → replayed POSTED
   * with the last leg's timestamp; otherwise a conflict on the first missing leg.
   */
  PostingOutcome mapReplayVerification(LegLookup legLookup);
}
