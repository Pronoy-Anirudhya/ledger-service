package com.bracits.ledgerservice.api.error.logging.impl;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.logging.RequestFailureLogger;
import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.bracits.ledgerservice.port.out.exception.LedgerUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.stereotype.Component;

/**
 * {@link RequestFailureLogger} implementation. Logs under the contract's name so the logger
 * category is unchanged.
 */
@Component
public final class RequestFailureLoggerImpl implements RequestFailureLogger {

  private static final Logger LOG = LoggerFactory.getLogger(RequestFailureLogger.class);

  @Override
  public void log(Exception ex, HttpStatusCode status, Object code) {
    String exception = ex.getClass().getSimpleName();

    if (isRetryable(ex)) {
      // Load shedding and ledger outages: frequent by nature, the caller retries; the fence
      // already alerts.
      LOG.warn(ApiConstants.LOG_REQUEST_FAILED, status.value(), code, exception);
    } else if (ex instanceof LedgerErrorException) {
      LOG.error(ApiConstants.LOG_REQUEST_FAILED, status.value(), code, exception);
    } else if (status.is5xxServerError()) {
      LOG.error(ApiConstants.LOG_REQUEST_FAILED, status.value(), code, exception, ex);
    } else if (LOG.isDebugEnabled()) {
      LOG.debug(ApiConstants.LOG_REQUEST_FAILED, status.value(), code, exception);
    }
  }

  /**
   * 503 causes: logged at warn without a stack trace.
   */
  private static boolean isRetryable(Exception ex) {
    return ex instanceof InvocationRejectedException || ex instanceof LedgerUnavailableException;
  }
}
