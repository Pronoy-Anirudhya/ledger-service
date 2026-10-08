package com.bracits.ledgerservice.port.out;

import com.bracits.ledgerservice.domain.account.Account;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import com.bracits.ledgerservice.domain.account.Balance;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingLookup;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import java.util.Optional;
import java.util.UUID;

/** Outbound port to the ledger (TigerBeetle). Every operation is idempotent and safe to retry. */
public interface LedgerStore {

  /**
   * Posts every leg atomically as one linked chain. Never throws for ledger outcomes; a timeout or
   * client error is returned as {@link PostingOutcome.Unknown}.
   */
  PostingOutcome createLinked(Posting posting);

  /**
   * Looks up the legs {@code 1..legCount} of a posting.
   *
   * @throws LedgerUnavailableException on timeout or client error
   */
  PostingLookup lookup(UUID postingId, int legCount);

  /**
   * Creates an account; an identical existing account is reported as {@code EXISTS}.
   *
   * @throws LedgerUnavailableException on timeout or client error
   */
  AccountCreation createAccount(Account account);

  /**
   * Reads an account's balances.
   *
   * @return empty if the account does not exist
   * @throws LedgerUnavailableException on timeout or client error
   */
  Optional<Balance> balance(UUID accountId);
}
