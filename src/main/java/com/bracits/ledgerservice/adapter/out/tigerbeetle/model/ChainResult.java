package com.bracits.ledgerservice.adapter.out.tigerbeetle.model;

import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;

/**
 * What the store must do with a {@code create_transfers} result.
 */
public sealed interface ChainResult {

  /**
   * The outcome is decided by the statuses alone.
   */
  record Decided(PostingOutcome outcome) implements ChainResult {

  }

  /**
   * The chain returned only {@code Exists}/{@code LinkedEventFailed} (or a {@code Created} mixed
   * with others): the legs must be looked up before reporting a replay (D8).
   */
  record VerifyReplay() implements ChainResult {

  }
}
