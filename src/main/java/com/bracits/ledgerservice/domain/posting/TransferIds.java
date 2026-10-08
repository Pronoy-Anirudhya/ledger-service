package com.bracits.ledgerservice.domain.posting;

import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.DomainValidationException;
import java.util.UUID;

/**
 * Factory for transfer IDs (spec 6.3). Transfer id of leg n = {@code postingId | n}, with n 1-based.
 * Deterministic, so every retry from any instance sends identical IDs.
 */
public final class TransferIds {

  private TransferIds() {}

  /** Returns the transfer id of leg {@code legIndex} (1-based) of the posting. */
  public static UUID legId(UUID postingId, int legIndex) {
    if (legIndex < DomainConstants.MIN_LEGS || legIndex > DomainConstants.MAX_LEGS) {
      throw new DomainValidationException(DomainConstants.MSG_LEG_INDEX_RANGE);
    }
    return new UUID(postingId.getMostSignificantBits(), postingId.getLeastSignificantBits() | legIndex);
  }

  /** True if the low byte of {@code postingId} is zero, i.e. free for the leg index. */
  public static boolean hasFreeLegByte(UUID postingId) {
    return (postingId.getLeastSignificantBits() & DomainConstants.LEG_INDEX_MASK) == 0L;
  }
}
