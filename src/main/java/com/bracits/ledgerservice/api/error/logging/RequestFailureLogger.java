package com.bracits.ledgerservice.api.error.logging;

import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.bracits.ledgerservice.port.out.exception.LedgerUnavailableException;
import org.springframework.http.HttpStatusCode;
import org.springframework.resilience.InvocationRejectedException;

/**
 * Logs one line per failed request, never the body. 503 causes
 * ({@link InvocationRejectedException}, {@link LedgerUnavailableException}) at warn without a stack
 * trace; {@link LedgerErrorException} at error without a stack trace; other 5xx at error with the
 * stack trace; 4xx at debug.
 */
public interface RequestFailureLogger {

  /**
   * Logs the failure of a request answered with {@code status} and problem {@code code}.
   */
  void log(Exception ex, HttpStatusCode status, Object code);
}
