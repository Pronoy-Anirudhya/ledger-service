package com.bracits.ledgerservice.api.error;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.domain.DomainValidationException;
import com.bracits.ledgerservice.port.out.LedgerErrorException;
import com.bracits.ledgerservice.port.out.LedgerUnavailableException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into {@code application/problem+json} with a {@code code} member (D18, D19).
 * Spring MVC's own errors (malformed JSON, 405, 415, missing parameter, type mismatch, bean
 * validation) go through {@link #handleExceptionInternal} and get {@code VALIDATION_FAILED} (4xx)
 * or {@code INTERNAL_ERROR} (5xx). 503s are logged at warn, other 5xx at error, 4xx at debug; bodies
 * are never logged.
 */
@RestControllerAdvice
public final class ProblemDetailAdvice extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailAdvice.class);

  private final ProblemDetailMapper problems;

  public ProblemDetailAdvice(ProblemDetailMapper problems) {
    this.problems = problems;
  }

  @ExceptionHandler(DomainValidationException.class)
  ResponseEntity<Object> handleDomainValidation(DomainValidationException ex) {
    return respond(ex, problems.validation(ex.getMessage(), List.of()));
  }

  /** The posting bulkhead is saturated: 503 at once, never queued (P10). */
  @ExceptionHandler(InvocationRejectedException.class)
  ResponseEntity<Object> handleOverloaded(InvocationRejectedException ex) {
    return respond(ex, problems.problem(ErrorCode.OVERLOADED, ApiConstants.DETAIL_OVERLOADED));
  }

  @ExceptionHandler(LedgerUnavailableException.class)
  ResponseEntity<Object> handleLedgerUnavailable(LedgerUnavailableException ex) {
    return respond(ex, problems.problem(ErrorCode.LEDGER_TIMEOUT, ApiConstants.DETAIL_LEDGER_UNAVAILABLE));
  }

  @ExceptionHandler(LedgerErrorException.class)
  ResponseEntity<Object> handleLedgerError(LedgerErrorException ex) {
    return respond(ex, problems.problem(ErrorCode.LEDGER_ERROR, ApiConstants.DETAIL_LEDGER_ERROR));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex) {
    return respond(ex, problems.problem(ErrorCode.INTERNAL_ERROR, ApiConstants.DETAIL_INTERNAL_ERROR));
  }

  /** Spring MVC's own exceptions: keep Spring's status, title and detail; add type, code and errors. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    ErrorCode code = statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED;
    ProblemDetail problem =
        body instanceof ProblemDetail detail
            ? problems.withCode(detail, code)
            : problems.problem(code, statusCode, ApiConstants.DETAIL_VALIDATION_FAILED);
    List<FieldViolation> errors = violationsOf(ex);
    if (!errors.isEmpty()) {
      problems.withErrors(problem, errors);
    }
    log(ex, statusCode, code);
    HttpHeaders merged = new HttpHeaders();
    merged.putAll(headers);
    merged.putAll(problems.headersFor(statusCode));
    return super.handleExceptionInternal(ex, problem, merged, statusCode, request);
  }

  private ResponseEntity<Object> respond(Exception ex, ProblemDetail problem) {
    HttpStatusCode status = HttpStatusCode.valueOf(problem.getStatus());
    Object code = problem.getProperties() == null ? null : problem.getProperties().get(ApiConstants.PROBLEM_CODE);
    log(ex, status, code);
    return problems.toResponse(problem);
  }

  private static void log(Exception ex, HttpStatusCode status, Object code) {
    String exception = ex.getClass().getSimpleName();
    if (isRetryable(ex)) {
      // Load shedding and ledger outages: frequent by nature, the caller retries; the fence already alerts.
      LOG.warn(ApiConstants.LOG_REQUEST_FAILED, status.value(), code, exception);
    } else if (ex instanceof LedgerErrorException) {
      LOG.error(ApiConstants.LOG_REQUEST_FAILED, status.value(), code, exception);
    } else if (status.is5xxServerError()) {
      LOG.error(ApiConstants.LOG_REQUEST_FAILED, status.value(), code, exception, ex);
    } else if (LOG.isDebugEnabled()) {
      LOG.debug(ApiConstants.LOG_REQUEST_FAILED, status.value(), code, exception);
    }
  }

  /** 503 causes: logged at warn without a stack trace. */
  private static boolean isRetryable(Exception ex) {
    return ex instanceof InvocationRejectedException || ex instanceof LedgerUnavailableException;
  }

  private static List<FieldViolation> violationsOf(Exception ex) {
    List<FieldViolation> violations = new ArrayList<>();
    switch (ex) {
      case MethodArgumentNotValidException notValid -> {
        for (FieldError error : notValid.getBindingResult().getFieldErrors()) {
          violations.add(new FieldViolation(error.getField(), error.getDefaultMessage()));
        }
        for (ObjectError error : notValid.getBindingResult().getGlobalErrors()) {
          violations.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage()));
        }
      }
      case HandlerMethodValidationException methodValidation ->
          methodValidation
              .getParameterValidationResults()
              .forEach(
                  result -> {
                    String field = result.getMethodParameter().getParameterName();
                    for (MessageSourceResolvable error : result.getResolvableErrors()) {
                      violations.add(new FieldViolation(field, error.getDefaultMessage()));
                    }
                  });
      default -> {
        // no field-level details
      }
    }
    return violations;
  }
}
