package com.bracits.ledgerservice.api.error;

import static com.bracits.ledgerservice.api.support.ApiFixtures.POSTING_ID;
import static com.bracits.ledgerservice.api.support.ApiFixtures.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.domain.posting.PostingStatus;
import com.bracits.ledgerservice.domain.posting.RejectionCode;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ProblemDetailMapperTest {

  private final ProblemDetailMapper mapper = new ProblemDetailMapper();

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void everyProblemHasTypeTitleStatusAndCode(ErrorCode code) {
    ProblemDetail problem = mapper.problem(code, "detail");

    assertThat(problem.getType()).isEqualTo(URI.create(ApiConstants.PROBLEM_TYPE_BASE + code.name()));
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
  void rejectionMapping(RejectionCode rejection, ErrorCode expectedCode, int expectedStatus) {
    ProblemDetail problem = mapper.rejected(POSTING_ID, new PostingOutcome.Rejected(rejection, 3));

    assertThat(mapper.errorCodeOf(rejection)).isEqualTo(expectedCode);
    assertThat(mapper.statusOf(rejection).value()).isEqualTo(expectedStatus);
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
    List<FieldViolation> errors = List.of(new FieldViolation("legs", "size must be between 1 and 8"));

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
    assertThat(problem.getProperties()).containsEntry(ApiConstants.PROBLEM_CODE, ErrorCode.VALIDATION_FAILED.name());
  }

  @Test
  void responseSetsProblemJsonAndRetryAfterOnlyOn503() {
    ResponseEntity<Object> unavailable = mapper.toResponse(mapper.problem(ErrorCode.OVERLOADED, "busy"));
    ResponseEntity<Object> rejected =
        mapper.toResponse(mapper.rejected(POSTING_ID, new PostingOutcome.Rejected(RejectionCode.INSUFFICIENT_FUNDS, 1)));

    assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(unavailable.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(unavailable.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo(ApiConstants.RETRY_AFTER_SECONDS);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
  }
}
