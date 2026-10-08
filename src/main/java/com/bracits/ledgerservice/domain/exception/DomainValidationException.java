package com.bracits.ledgerservice.domain.exception;

/**
 * A request violates a domain invariant (e.g. low byte of postingId not zero). Maps to 400.
 */
public final class DomainValidationException extends RuntimeException {

  public DomainValidationException(String message) {
    super(message);
  }
}
