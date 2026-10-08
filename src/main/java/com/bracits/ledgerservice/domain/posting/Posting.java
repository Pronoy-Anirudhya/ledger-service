package com.bracits.ledgerservice.domain.posting;

import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.DomainValidationException;
import java.util.List;
import java.util.UUID;

/**
 * An atomic multi-leg posting. All legs post together or none do.
 *
 * @param postingId 128-bit id whose low byte is 0; leg n has transfer id {@code postingId | n}
 * @param product {@code user_data_32} of every leg (e.g. 1 = SEND_MONEY)
 * @param userData64 {@code user_data_64} of every leg (e.g. the sender's wallet id)
 * @param legs 1..{@link DomainConstants#MAX_LEGS} legs, in order
 */
public record Posting(UUID postingId, int product, long userData64, List<Leg> legs) {

  public Posting {
    if (postingId == null) {
      throw new DomainValidationException(DomainConstants.MSG_POSTING_ID_REQUIRED);
    }
    if (!TransferIds.hasFreeLegByte(postingId)) {
      throw new DomainValidationException(DomainConstants.MSG_POSTING_ID_LOW_BYTE);
    }
    if (product < 0) {
      throw new DomainValidationException(DomainConstants.MSG_PRODUCT_NEGATIVE);
    }
    if (legs == null || legs.isEmpty() || legs.size() > DomainConstants.MAX_LEGS) {
      throw new DomainValidationException(DomainConstants.MSG_LEG_COUNT.formatted(DomainConstants.MAX_LEGS));
    }
    legs = List.copyOf(legs);
  }

  /** Number of legs. */
  public int legCount() {
    return legs.size();
  }
}
