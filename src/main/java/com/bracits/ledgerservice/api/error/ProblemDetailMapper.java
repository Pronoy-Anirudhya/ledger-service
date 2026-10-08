package com.bracits.ledgerservice.api.error;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.domain.posting.PostingStatus;
import com.bracits.ledgerservice.domain.posting.RejectionCode;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds every RFC 9457 {@link ProblemDetail} of the API (D18): {@code type} = {@link
 * ApiConstants#PROBLEM_TYPE_BASE} + code, {@code title} from {@link ErrorCode}, the HTTP status, a
 * {@code detail}, and the extension {@code code} on every problem.
 */
@Component
public final class ProblemDetailMapper {

  /** A problem with the code's default status. */
  public ProblemDetail problem(ErrorCode code, String detail) {
    return problem(code, code.status(), detail);
  }

  /** A problem with an explicit status (e.g. ACCOUNT_NOT_FOUND is 404 on the balance endpoint). */
  public ProblemDetail problem(ErrorCode code, HttpStatusCode status, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(code.title());
    return withCode(problem, code);
  }

  /** Adds {@code type} and {@code code} to a problem built elsewhere (e.g. by Spring MVC), keeping its title. */
  public ProblemDetail withCode(ProblemDetail problem, ErrorCode code) {
    problem.setType(typeOf(code));
    problem.setProperty(ApiConstants.PROBLEM_CODE, code.name());
    return problem;
  }

  /** 400 {@code VALIDATION_FAILED} with the {@code errors} list. */
  public ProblemDetail validation(String detail, List<FieldViolation> errors) {
    ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, detail);
    problem.setProperty(ApiConstants.PROBLEM_ERRORS, List.copyOf(errors));
    return problem;
  }

  /** Adds the {@code errors} list to a validation problem built elsewhere. */
  public ProblemDetail withErrors(ProblemDetail problem, List<FieldViolation> errors) {
    problem.setProperty(ApiConstants.PROBLEM_ERRORS, List.copyOf(errors));
    return problem;
  }

  /** 422 / 409 / 500 for a rejected posting, with {@code postingId}, {@code postingStatus} and {@code legIndex}. */
  public ProblemDetail rejected(UUID postingId, PostingOutcome.Rejected rejected) {
    ErrorCode code = errorCodeOf(rejected.code());
    ProblemDetail problem =
        problem(
            code,
            statusOf(rejected.code()),
            ApiConstants.DETAIL_POSTING_REJECTED.formatted(postingId, rejected.legIndex()));
    problem.setProperty(ApiConstants.PROBLEM_POSTING_ID, postingId);
    problem.setProperty(ApiConstants.PROBLEM_POSTING_STATUS, PostingStatus.REJECTED.name());
    problem.setProperty(ApiConstants.PROBLEM_LEG_INDEX, rejected.legIndex());
    return problem;
  }

  /** 503 {@code LEDGER_TIMEOUT} for a posting whose outcome is unknown. */
  public ProblemDetail unknown(UUID postingId) {
    ProblemDetail problem =
        problem(ErrorCode.LEDGER_TIMEOUT, ApiConstants.DETAIL_POSTING_UNKNOWN.formatted(postingId));
    problem.setProperty(ApiConstants.PROBLEM_POSTING_ID, postingId);
    problem.setProperty(ApiConstants.PROBLEM_POSTING_STATUS, PostingStatus.UNKNOWN.name());
    return problem;
  }

  /** 409 {@code ACCOUNT_CONFLICT}. */
  public ProblemDetail accountConflict(UUID accountId) {
    ProblemDetail problem =
        problem(ErrorCode.ACCOUNT_CONFLICT, ApiConstants.DETAIL_ACCOUNT_CONFLICT.formatted(accountId));
    problem.setProperty(ApiConstants.PROBLEM_ACCOUNT_ID, accountId);
    return problem;
  }

  /** 404 {@code ACCOUNT_NOT_FOUND} (balance endpoint). */
  public ProblemDetail accountNotFound(UUID accountId) {
    ProblemDetail problem =
        problem(
            ErrorCode.ACCOUNT_NOT_FOUND,
            HttpStatus.NOT_FOUND,
            ApiConstants.DETAIL_ACCOUNT_NOT_FOUND.formatted(accountId));
    problem.setProperty(ApiConstants.PROBLEM_ACCOUNT_ID, accountId);
    return problem;
  }

  /**
   * Wraps a problem in a response: its status, {@code application/problem+json}, and {@code
   * Retry-After} on every 503.
   */
  public ResponseEntity<Object> toResponse(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .headers(headersFor(HttpStatus.valueOf(problem.getStatus())))
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /** {@code Retry-After} on 503, nothing otherwise. */
  public HttpHeaders headersFor(HttpStatusCode status) {
    HttpHeaders headers = new HttpHeaders();
    if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
      headers.set(HttpHeaders.RETRY_AFTER, ApiConstants.RETRY_AFTER_SECONDS);
    }
    return headers;
  }

  /** The error code of a rejection. */
  public ErrorCode errorCodeOf(RejectionCode code) {
    return switch (code) {
      case INSUFFICIENT_FUNDS -> ErrorCode.INSUFFICIENT_FUNDS;
      case ACCOUNT_NOT_FOUND -> ErrorCode.ACCOUNT_NOT_FOUND;
      case PREVIOUSLY_REJECTED -> ErrorCode.PREVIOUSLY_REJECTED;
      case POSTING_CONFLICT -> ErrorCode.POSTING_CONFLICT;
      case LEDGER_ERROR -> ErrorCode.LEDGER_ERROR;
    };
  }

  /** The HTTP status of a rejection (D19). */
  public HttpStatus statusOf(RejectionCode code) {
    return switch (code) {
      case INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, PREVIOUSLY_REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT;
      case POSTING_CONFLICT -> HttpStatus.CONFLICT;
      case LEDGER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private static URI typeOf(ErrorCode code) {
    return URI.create(ApiConstants.PROBLEM_TYPE_BASE + code.name());
  }
}
