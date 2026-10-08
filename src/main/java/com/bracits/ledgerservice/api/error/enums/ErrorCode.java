package com.bracits.ledgerservice.api.error.enums;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable error codes ({@code code} member of every Problem Details body).
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Invalid request"),
  INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient funds"),
  ACCOUNT_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "Account not found"),
  PREVIOUSLY_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "Posting previously rejected"),
  POSTING_CONFLICT(HttpStatus.CONFLICT, "Posting conflict"),
  ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "Account conflict"),
  LEDGER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Ledger error"),
  /**
   * An unexpected failure inside ledger-service itself (not a ledger result).
   */
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
  LEDGER_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "Ledger unavailable"),
  OVERLOADED(HttpStatus.SERVICE_UNAVAILABLE, "Overloaded");

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  /**
   * Default HTTP status (ACCOUNT_NOT_FOUND is 404 on the balance endpoint).
   */
  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }
}
