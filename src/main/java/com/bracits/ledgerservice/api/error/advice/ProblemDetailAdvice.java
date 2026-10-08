package com.bracits.ledgerservice.api.error.advice;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.dto.FieldViolation;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.error.logging.RequestFailureLogger;
import com.bracits.ledgerservice.api.error.mapper.FieldViolationMapper;
import com.bracits.ledgerservice.api.error.mapper.ProblemDetailMapper;
import com.bracits.ledgerservice.api.error.mapper.ProblemResponseMapper;
import com.bracits.ledgerservice.domain.exception.DomainValidationException;
import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.bracits.ledgerservice.port.out.exception.LedgerUnavailableException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into {@code application/problem+json} with a {@code code} member (D18, D19).
 * Spring MVC's own errors (malformed JSON, 405, 415, missing parameter, type mismatch, bean
 * validation) go through {@link #handleExceptionInternal} and get {@code VALIDATION_FAILED} (4xx)
 * or {@code INTERNAL_ERROR} (5xx). {@link FieldViolationMapper} extracts the field errors and
 * {@link RequestFailureLogger} logs every failure; bodies are never logged.
 */
@RestControllerAdvice
public final class ProblemDetailAdvice extends ResponseEntityExceptionHandler {

  private final ProblemDetailMapper problems;
  private final ProblemResponseMapper problemResponses;
  private final FieldViolationMapper fieldViolations;
  private final RequestFailureLogger failureLogger;

  public ProblemDetailAdvice(
      ProblemDetailMapper problems,
      ProblemResponseMapper problemResponses,
      FieldViolationMapper fieldViolations,
      RequestFailureLogger failureLogger) {
    this.problems = problems;
    this.problemResponses = problemResponses;
    this.fieldViolations = fieldViolations;
    this.failureLogger = failureLogger;
  }

  @ExceptionHandler(DomainValidationException.class)
  ResponseEntity<Object> handleDomainValidation(DomainValidationException ex) {
    return respond(ex, problems.validation(ex.getMessage(), List.of()));
  }

  /**
   * The posting bulkhead is saturated: 503 at once, never queued (P10).
   */
  @ExceptionHandler(InvocationRejectedException.class)
  ResponseEntity<Object> handleOverloaded(InvocationRejectedException ex) {
    return respond(ex, problems.problem(ErrorCode.OVERLOADED, ApiConstants.DETAIL_OVERLOADED));
  }

  @ExceptionHandler(LedgerUnavailableException.class)
  ResponseEntity<Object> handleLedgerUnavailable(LedgerUnavailableException ex) {
    return respond(
        ex, problems.problem(ErrorCode.LEDGER_TIMEOUT, ApiConstants.DETAIL_LEDGER_UNAVAILABLE));
  }

  @ExceptionHandler(LedgerErrorException.class)
  ResponseEntity<Object> handleLedgerError(LedgerErrorException ex) {
    return respond(ex, problems.problem(ErrorCode.LEDGER_ERROR, ApiConstants.DETAIL_LEDGER_ERROR));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex) {
    return respond(
        ex, problems.problem(ErrorCode.INTERNAL_ERROR, ApiConstants.DETAIL_INTERNAL_ERROR));
  }

  /**
   * Spring MVC's own exceptions: keep Spring's status, title and detail; add type, code and
   * errors.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ErrorCode code =
        statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED;

    ProblemDetail problem =
        body instanceof ProblemDetail detail
            ? problems.withCode(detail, code)
            : problems.problem(code, statusCode, ApiConstants.DETAIL_VALIDATION_FAILED);
    List<FieldViolation> errors = fieldViolations.violationsOf(ex);
    if (!errors.isEmpty()) {
      problems.withErrors(problem, errors);
    }

    failureLogger.log(ex, statusCode, code);

    HttpHeaders merged = new HttpHeaders();
    merged.putAll(headers);
    merged.putAll(problemResponses.headersFor(statusCode));

    return super.handleExceptionInternal(ex, problem, merged, statusCode, request);
  }

  private ResponseEntity<Object> respond(Exception ex, ProblemDetail problem) {
    HttpStatusCode status = HttpStatusCode.valueOf(problem.getStatus());
    Object code =
        problem.getProperties() == null
            ? null
            : problem.getProperties().get(ApiConstants.PROBLEM_CODE);

    failureLogger.log(ex, status, code);

    return problemResponses.toResponse(problem);
  }
}
