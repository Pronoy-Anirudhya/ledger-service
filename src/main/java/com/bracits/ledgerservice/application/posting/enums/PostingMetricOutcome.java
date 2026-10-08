package com.bracits.ledgerservice.application.posting.enums;

/**
 * Value of the {@code outcome} tag on the posting timer and the posting log line.
 */
public enum PostingMetricOutcome {
  /**
   * Every leg committed (new or replay).
   */
  POSTED,
  /**
   * The ledger definitively did not post, or the request conflicts.
   */
  REJECTED,
  /**
   * Timeout or client error; the outcome is unknown.
   */
  UNKNOWN,
  /**
   * The store threw an unexpected exception.
   */
  ERROR
}
