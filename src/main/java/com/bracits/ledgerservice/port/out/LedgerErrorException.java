package com.bracits.ledgerservice.port.out;

/** The ledger returned a result the service does not expect (a validation bug). Maps to 500. */
public final class LedgerErrorException extends RuntimeException {

  public LedgerErrorException(String message) {
    super(message);
  }
}
