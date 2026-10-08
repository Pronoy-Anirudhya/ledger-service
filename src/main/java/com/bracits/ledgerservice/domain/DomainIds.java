package com.bracits.ledgerservice.domain;

import java.util.UUID;

/** Shared checks for 128-bit ledger IDs. */
public final class DomainIds {

  private DomainIds() {}

  /** Returns the id if it is present and non-zero; throws {@link DomainValidationException} otherwise. */
  public static UUID requireNonZero(UUID id, String message) {
    if (id == null || DomainConstants.ZERO_ID.equals(id)) {
      throw new DomainValidationException(message);
    }
    return id;
  }

  /** Throws {@link DomainValidationException} unless {@code code} is a valid TigerBeetle code (1..65535). */
  public static int requireValidCode(int code) {
    if (code < DomainConstants.MIN_CODE || code > DomainConstants.MAX_CODE) {
      throw new DomainValidationException(DomainConstants.MSG_CODE_RANGE);
    }
    return code;
  }
}
