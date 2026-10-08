package com.bracits.ledgerservice.port.out;

/** The ledger did not answer within the deadline, or the client failed. The outcome is unknown. */
public final class LedgerUnavailableException extends RuntimeException {

  public LedgerUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
