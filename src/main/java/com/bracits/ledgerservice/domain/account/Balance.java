package com.bracits.ledgerservice.domain.account;

import java.util.UUID;

/**
 * Account balances in minor units.
 *
 * @param accountId the account
 * @param debitsPosted posted debits
 * @param creditsPosted posted credits
 * @param debitsPending pending debits
 * @param creditsPending pending credits
 */
public record Balance(
    UUID accountId, long debitsPosted, long creditsPosted, long debitsPending, long creditsPending) {

  /** Credit-normal available balance: credits posted − debits posted − debits pending. */
  public long available() {
    return creditsPosted - debitsPosted - debitsPending;
  }
}
