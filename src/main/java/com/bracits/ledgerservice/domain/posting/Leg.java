package com.bracits.ledgerservice.domain.posting;

import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.DomainIds;
import com.bracits.ledgerservice.domain.DomainValidationException;
import java.util.UUID;

/**
 * One leg of a posting: move {@code amount} minor units from the debit to the credit account.
 *
 * @param debitAccountId account debited (non-zero)
 * @param creditAccountId account credited (non-zero, different from the debit account)
 * @param amount minor units, &gt; 0
 * @param code transfer code, 1..65535 (e.g. 10 principal, 11 fee)
 */
public record Leg(UUID debitAccountId, UUID creditAccountId, long amount, int code) {

  public Leg {
    DomainIds.requireNonZero(debitAccountId, DomainConstants.MSG_ACCOUNT_ID_REQUIRED);
    DomainIds.requireNonZero(creditAccountId, DomainConstants.MSG_ACCOUNT_ID_REQUIRED);
    if (debitAccountId.equals(creditAccountId)) {
      throw new DomainValidationException(DomainConstants.MSG_SAME_ACCOUNT);
    }
    if (amount <= 0) {
      throw new DomainValidationException(DomainConstants.MSG_AMOUNT_POSITIVE);
    }
    DomainIds.requireValidCode(code);
  }
}
