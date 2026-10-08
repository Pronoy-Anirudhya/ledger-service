package com.bracits.ledgerservice.domain.account;

import com.bracits.ledgerservice.domain.DomainConstants;
import java.util.Set;
import java.util.UUID;

/**
 * Fixed chart-of-accounts entries (spec 6.2). The id is the code in the low bytes of an otherwise
 * zero 128-bit id, e.g. fee income = {@code 00000000-0000-0000-0000-0000000000c8}.
 */
public enum SystemAccount {
  FEE_INCOME(200),
  VAT_PAYABLE(210),
  COMMISSION_PAYABLE(220),
  EMONEY_ISSUANCE(900);

  private final int code;

  SystemAccount(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  public UUID id() {
    return new UUID(0L, code);
  }

  /** The account to create at start-up (no flags, {@code user_data_64} = 0). */
  public Account toAccount() {
    return new Account(id(), code, Set.of(), DomainConstants.SYSTEM_ACCOUNT_USER_DATA_64);
  }
}
