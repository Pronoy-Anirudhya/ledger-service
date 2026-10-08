package com.bracits.ledgerservice.domain.account.enums;

/**
 * Ledger account flags (mapped to TigerBeetle bit flags in the adapter).
 */
public enum AccountFlag {
  /**
   * The account cannot be overdrawn (customer wallets).
   */
  DEBITS_MUST_NOT_EXCEED_CREDITS,
  /**
   * The account's credits are capped by its debits.
   */
  CREDITS_MUST_NOT_EXCEED_DEBITS,
  /**
   * TigerBeetle keeps balance history for the account.
   */
  HISTORY
}
