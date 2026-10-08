package com.bracits.ledgerservice.api.error.mapper.impl;

import static com.bracits.ledgerservice.api.support.ApiFixtures.POSTING_ID;
import static com.bracits.ledgerservice.api.support.ApiFixtures.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.dto.FieldViolation;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.error.mapper.ProblemDetailMapper;
import com.bracits.ledgerservice.domain.posting.enums.PostingStatus;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailMapperImplTest {

  private final ProblemDetailMapper mapper =
      new ProblemDetailMapperImpl(new RejectionErrorMapperImpl());

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void everyProblemHasTypeTitleStatusAndCode(ErrorCode code) {
    ProblemDetail problem = mapper.problem(code, "detail");

    assertThat(problem.getType())
        .isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE + code.name()));
    assertThat(problem.getTitle()).isEqualTo(code.title());
    assertThat(problem.getStatus()).isEqualTo(code.status().value());
    assertThat(problem.getDetail()).isEqualTo("detail");
    assertThat(problem.getProperties()).containsEntry(ApiConstants.PROBLEM_CODE, code.name());
  }

  @ParameterizedTest
  @CsvSource({
      "INSUFFICIENT_FUNDS, INSUFFICIENT_FUNDS, 422",
      "ACCOUNT_NOT_FOUND, ACCOUNT_NOT_FOUND, 422",
      "PREVIOUSLY_REJECTED, PREVIOUSLY_REJECTED, 422",
      "POSTING_CONFLICT, POSTING_CONFLICT, 409",
      "LEDGER_ERROR, LEDGER_ERROR, 500"
  })
  void rejectedProblemCarriesPostingMembers(
      RejectionCode rejection, ErrorCode expectedCode, int expectedStatus) {
    PostingOutcome.Rejected rejected = new PostingOutcome.Rejected(rejection, 3);

    ProblemDetail problem = mapper.rejected(POSTING_ID, rejected);

    assertThat(problem.getStatus()).isEqualTo(expectedStatus);
    assertThat(problem.getProperties())
        .containsEntry(ApiConstants.PROBLEM_CODE, expectedCode.name())
        .containsEntry(ApiConstants.PROBLEM_POSTING_ID, POSTING_ID)
        .containsEntry(ApiConstants.PROBLEM_POSTING_STATUS, PostingStatus.REJECTED.name())
        .containsEntry(ApiConstants.PROBLEM_LEG_INDEX, 3);
  }

  @Test
  void unknownIs503LedgerTimeout() {
    ProblemDetail problem = mapper.unknown(POSTING_ID);

    assertThat(problem.getStatus()).isEqualTo(503);
    assertThat(problem.getProperties())
        .containsEntry(ApiConstants.PROBLEM_CODE, ErrorCode.LEDGER_TIMEOUT.name())
        .containsEntry(ApiConstants.PROBLEM_POSTING_STATUS, PostingStatus.UNKNOWN.name())
        .doesNotContainKey(ApiConstants.PROBLEM_LEG_INDEX);
  }

  @Test
  void accountNotFoundIs404() {
    ProblemDetail problem = mapper.accountNotFound(SENDER);

    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getProperties())
        .containsEntry(ApiConstants.PROBLEM_CODE, ErrorCode.ACCOUNT_NOT_FOUND.name())
        .containsEntry(ApiConstants.PROBLEM_ACCOUNT_ID, SENDER);
  }

  @Test
  void accountConflictIs409() {
    assertThat(mapper.accountConflict(SENDER).getStatus()).isEqualTo(409);
  }

  @Test
  void validationCarriesErrors() {
    List<FieldViolation> errors =
        List.of(new FieldViolation("legs", "size must be between 1 and 8"));

    ProblemDetail problem = mapper.validation("bad", errors);

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getProperties())
        .containsEntry(ApiConstants.PROBLEM_CODE, ErrorCode.VALIDATION_FAILED.name())
        .containsEntry(ApiConstants.PROBLEM_ERRORS, errors);
  }

  @Test
  void withCodeKeepsSpringTitle() {
    ProblemDetail spring = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
    spring.setTitle("Method Not Allowed");

    ProblemDetail problem = mapper.withCode(spring, ErrorCode.VALIDATION_FAILED);

    assertThat(problem.getTitle()).isEqualTo("Method Not Allowed");
    assertThat(problem.getType())
        .isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE + ErrorCode.VALIDATION_FAILED.name()));
    assertThat(problem.getProperties())
        .containsEntry(ApiConstants.PROBLEM_CODE, ErrorCode.VALIDATION_FAILED.name());
  }
}
