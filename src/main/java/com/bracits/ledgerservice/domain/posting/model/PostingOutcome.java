package com.bracits.ledgerservice.domain.posting.model;

import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;

/**
 * Result of a posting attempt. Handle with an exhaustive {@code switch}.
 */
public sealed interface PostingOutcome {

  /**
   * Every leg is committed in the ledger.
   *
   * @param ledgerTimestamp TigerBeetle timestamp (ns) of the last leg
   * @param replay          true if the posting already existed (an idempotent retry)
   */
  record Posted(long ledgerTimestamp, boolean replay) implements PostingOutcome {

  }

  /**
   * The ledger definitively did not post (nothing moved), or the request conflicts.
   *
   * @param code     reason
   * @param legIndex 1-based root-cause leg
   */
  record Rejected(RejectionCode code, int legIndex) implements PostingOutcome {

  }

  /**
   * The outcome is unknown (timeout or client error); the client has been fenced. Safe to retry.
   *
   * @param reason short diagnostic
   */
  record Unknown(String reason) implements PostingOutcome {

  }
}
