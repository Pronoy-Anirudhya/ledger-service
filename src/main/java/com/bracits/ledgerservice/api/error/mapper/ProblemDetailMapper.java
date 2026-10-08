package com.bracits.ledgerservice.api.error.mapper;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.dto.FieldViolation;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds every RFC 9457 {@link ProblemDetail} of the API (D18): {@code type} =
 * {@link ApiConstants#PROBLEM_TYPE_BASE} + code, {@code title} from {@link ErrorCode}, the HTTP
 * status, a {@code detail}, and the extension {@code code} on every problem.
 * {@link ProblemResponseMapper} wraps the result in a response.
 */
public interface ProblemDetailMapper {

  /**
   * A problem with the code's default status.
   */
  ProblemDetail problem(ErrorCode code, String detail);

  /**
   * A problem with an explicit status (e.g. ACCOUNT_NOT_FOUND is 404 on the balance endpoint).
   */
  ProblemDetail problem(ErrorCode code, HttpStatusCode status, String detail);

  /**
   * Adds {@code type} and {@code code} to a problem built elsewhere (e.g. by Spring MVC), keeping
   * its title.
   */
  ProblemDetail withCode(ProblemDetail problem, ErrorCode code);

  /**
   * 400 {@code VALIDATION_FAILED} with the {@code errors} list.
   */
  ProblemDetail validation(String detail, List<FieldViolation> errors);

  /**
   * Adds the {@code errors} list to a validation problem built elsewhere.
   */
  ProblemDetail withErrors(ProblemDetail problem, List<FieldViolation> errors);

  /**
   * 422 / 409 / 500 for a rejected posting, with {@code postingId}, {@code postingStatus} and
   * {@code legIndex}.
   */
  ProblemDetail rejected(UUID postingId, PostingOutcome.Rejected rejected);

  /**
   * 503 {@code LEDGER_TIMEOUT} for a posting whose outcome is unknown.
   */
  ProblemDetail unknown(UUID postingId);

  /**
   * 409 {@code ACCOUNT_CONFLICT}.
   */
  ProblemDetail accountConflict(UUID accountId);

  /**
   * 404 {@code ACCOUNT_NOT_FOUND} (balance endpoint).
   */
  ProblemDetail accountNotFound(UUID accountId);
}
