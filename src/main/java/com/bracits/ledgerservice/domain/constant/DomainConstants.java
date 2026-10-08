package com.bracits.ledgerservice.domain.constant;

import java.util.UUID;

/**
 * Ledger-wide domain constants. Framework-free: no Spring or TigerBeetle types.
 */
public final class DomainConstants {

  /**
   * Minimum number of legs in one posting.
   */
  public static final int MIN_LEGS = 1;

  /**
   * Hard maximum number of legs in one posting (the leg index must fit in the postingId's low
   * byte).
   */
  public static final int MAX_LEGS = 8;

  /**
   * The low byte of a postingId is reserved for the leg index and must be zero.
   */
  public static final long LEG_INDEX_MASK = 0xFFL;

  /**
   * Smallest and largest valid account or transfer {@code code} (TigerBeetle u16, non-zero).
   */
  public static final int MIN_CODE = 1;
  public static final int MAX_CODE = 65_535;

  /**
   * The all-zero 128-bit ID, which TigerBeetle rejects for accounts and transfers.
   */
  public static final UUID ZERO_ID = new UUID(0L, 0L);

  /**
   * Transfer code for funding (cash-in from the e-money issuance account), spec 6.2.
   */
  public static final int FUNDING_TRANSFER_CODE = 1;

  /**
   * {@code user_data_32} of a funding transfer (no product).
   */
  public static final int FUNDING_PRODUCT = 0;

  /**
   * {@code user_data_64} of a funding transfer.
   */
  public static final long FUNDING_USER_DATA_64 = 0L;

  /**
   * {@code user_data_64} of a system account.
   */
  public static final long SYSTEM_ACCOUNT_USER_DATA_64 = 0L;

  /**
   * Validation messages for postings.
   */
  public static final String MSG_POSTING_ID_REQUIRED = "postingId is required";
  public static final String MSG_POSTING_ID_LOW_BYTE = "the low byte of postingId must be 0";
  public static final String MSG_LEG_COUNT =
      "a posting must have between " + MIN_LEGS + " and %d legs";
  public static final String MSG_PRODUCT_NEGATIVE = "product must be >= 0";
  public static final String MSG_LEG_INDEX_RANGE =
      "leg index must be between " + MIN_LEGS + " and " + MAX_LEGS;

  /**
   * Validation messages for legs and accounts.
   */
  public static final String MSG_ACCOUNT_ID_REQUIRED =
      "account id is required and must be non-zero";
  public static final String MSG_AMOUNT_POSITIVE = "amount must be > 0";
  public static final String MSG_CODE_RANGE =
      "code must be between " + MIN_CODE + " and " + MAX_CODE;
  public static final String MSG_SAME_ACCOUNT = "debit and credit account must differ";

  private DomainConstants() {
  }
}
