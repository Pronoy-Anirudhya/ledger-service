package com.bracits.ledgerservice.domain.account;

import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.DomainIds;
import java.util.Set;
import java.util.UUID;

/**
 * A ledger account to create.
 *
 * @param accountId non-zero 128-bit id
 * @param code account code, 1..65535 (e.g. 100 customer wallet)
 * @param flags account flags
 * @param userData64 back-reference (e.g. wallet id); 0 for system accounts
 */
public record Account(UUID accountId, int code, Set<AccountFlag> flags, long userData64) {

  public Account {
    DomainIds.requireNonZero(accountId, DomainConstants.MSG_ACCOUNT_ID_REQUIRED);
    DomainIds.requireValidCode(code);
    flags = flags == null ? Set.of() : Set.copyOf(flags);
  }
}
