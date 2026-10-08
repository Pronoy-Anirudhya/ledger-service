package com.bracits.ledgerservice.domain.posting.enums;

/**
 * Why a posting was not posted (spec 8.2 result mapping).
 */
public enum RejectionCode {
  /**
   * {@code exceeds_credits}: the debit account's available balance is too low.
   */
  INSUFFICIENT_FUNDS,
  /**
   * {@code debit_account_not_found} / {@code credit_account_not_found}.
   */
  ACCOUNT_NOT_FOUND,
  /**
   * {@code id_already_failed}: this posting failed definitively in an earlier attempt.
   */
  PREVIOUSLY_REJECTED,
  /**
   * {@code exists_with_different_*}: same id, different content (a caller bug).
   */
  POSTING_CONFLICT,
  /**
   * Any other ledger result: a validation bug.
   */
  LEDGER_ERROR
}
