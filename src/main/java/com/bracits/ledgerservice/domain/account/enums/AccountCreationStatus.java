package com.bracits.ledgerservice.domain.account.enums;

/**
 * Outcome of creating an account.
 */
public enum AccountCreationStatus {
  /**
   * Newly created.
   */
  CREATED,
  /**
   * Already existed with identical fields (idempotent retry).
   */
  EXISTS,
  /**
   * Already existed with different fields.
   */
  CONFLICT
}
