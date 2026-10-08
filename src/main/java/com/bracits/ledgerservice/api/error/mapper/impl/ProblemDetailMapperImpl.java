package com.bracits.ledgerservice.api.error.mapper.impl;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.dto.FieldViolation;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.error.mapper.ProblemDetailMapper;
import com.bracits.ledgerservice.api.error.mapper.RejectionErrorMapper;
import com.bracits.ledgerservice.domain.posting.enums.PostingStatus;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * {@link ProblemDetailMapper} implementation.
 */
@Component
public final class ProblemDetailMapperImpl implements ProblemDetailMapper {

  private final RejectionErrorMapper rejectionErrors;

  public ProblemDetailMapperImpl(RejectionErrorMapper rejectionErrors) {
    this.rejectionErrors = rejectionErrors;
  }

  @Override
  public ProblemDetail problem(ErrorCode code, String detail) {
    return problem(code, code.status(), detail);
  }

  @Override
  public ProblemDetail problem(ErrorCode code, HttpStatusCode status, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(code.title());

    return withCode(problem, code);
  }

  @Override
  public ProblemDetail withCode(ProblemDetail problem, ErrorCode code) {
    problem.setType(typeOf(code));
    problem.setProperty(ApiConstants.PROBLEM_CODE, code.name());

    return problem;
  }

  @Override
  public ProblemDetail validation(String detail, List<FieldViolation> errors) {
    ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, detail);

    return withErrors(problem, errors);
  }

  @Override
  public ProblemDetail withErrors(ProblemDetail problem, List<FieldViolation> errors) {
    problem.setProperty(ApiConstants.PROBLEM_ERRORS, List.copyOf(errors));

    return problem;
  }

  @Override
  public ProblemDetail rejected(UUID postingId, PostingOutcome.Rejected rejected) {
    ErrorCode code = rejectionErrors.errorCodeOf(rejected.code());
    HttpStatus status = rejectionErrors.statusOf(rejected.code());
    String detail = ApiConstants.DETAIL_POSTING_REJECTED.formatted(postingId, rejected.legIndex());

    ProblemDetail problem = problem(code, status, detail);
    problem.setProperty(ApiConstants.PROBLEM_POSTING_ID, postingId);
    problem.setProperty(ApiConstants.PROBLEM_POSTING_STATUS, PostingStatus.REJECTED.name());
    problem.setProperty(ApiConstants.PROBLEM_LEG_INDEX, rejected.legIndex());

    return problem;
  }

  @Override
  public ProblemDetail unknown(UUID postingId) {
    String detail = ApiConstants.DETAIL_POSTING_UNKNOWN.formatted(postingId);

    ProblemDetail problem = problem(ErrorCode.LEDGER_TIMEOUT, detail);
    problem.setProperty(ApiConstants.PROBLEM_POSTING_ID, postingId);
    problem.setProperty(ApiConstants.PROBLEM_POSTING_STATUS, PostingStatus.UNKNOWN.name());

    return problem;
  }

  @Override
  public ProblemDetail accountConflict(UUID accountId) {
    String detail = ApiConstants.DETAIL_ACCOUNT_CONFLICT.formatted(accountId);

    ProblemDetail problem = problem(ErrorCode.ACCOUNT_CONFLICT, detail);
    problem.setProperty(ApiConstants.PROBLEM_ACCOUNT_ID, accountId);

    return problem;
  }

  @Override
  public ProblemDetail accountNotFound(UUID accountId) {
    String detail = ApiConstants.DETAIL_ACCOUNT_NOT_FOUND.formatted(accountId);

    ProblemDetail problem = problem(ErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND, detail);
    problem.setProperty(ApiConstants.PROBLEM_ACCOUNT_ID, accountId);

    return problem;
  }

  private static URI typeOf(ErrorCode code) {
    return URI.create(ApiConstants.PROBLEM_TYPE_BASE + code.name());
  }
}
