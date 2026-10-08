package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegLookup;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegResult;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.tigerbeetle.CreateTransferResultBatch;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.TransferBatch;
import java.util.List;
import java.util.UUID;

/**
 * Converts postings to TigerBeetle transfer batches and transfer results back to adapter and domain
 * types. TigerBeetle types never leave this adapter package.
 */
public interface TransferBatchMapper {

  /**
   * One row per leg: id = {@code postingId | n} (n 1-based), {@code LINKED} on every leg but the
   * last, {@code user_data_128} = postingId, {@code user_data_64} and {@code user_data_32} from the
   * posting, {@code timeout} = 0.
   */
  TransferBatch toTransferBatch(Posting posting);

  /**
   * The ids of legs {@code 1..legCount} of a posting.
   */
  IdBatch toLegIdBatch(UUID postingId, int legCount);

  /**
   * One (status, timestamp) per event, in event order.
   */
  List<LegResult> toLegResults(CreateTransferResultBatch results);

  /**
   * Checks which of legs {@code 1..legCount} are among the transfers found.
   */
  LegLookup toLegLookup(UUID postingId, int legCount, TransferBatch found);

  /**
   * POSTED with the last leg's timestamp only if every leg was found, otherwise NOT_FOUND (D24).
   */
  PostingLookup toPostingLookup(UUID postingId, LegLookup legLookup);
}
