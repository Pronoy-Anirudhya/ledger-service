package com.bracits.ledgerservice.api.error.mapper.impl;

import static com.bracits.ledgerservice.api.support.ApiFixtures.POSTING_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.error.mapper.ProblemDetailMapper;
import com.bracits.ledgerservice.api.error.mapper.ProblemResponseMapper;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ProblemResponseMapperImplTest {

  private final ProblemDetailMapper problems =
      new ProblemDetailMapperImpl(new RejectionErrorMapperImpl());
  private final ProblemResponseMapper mapper = new ProblemResponseMapperImpl();

  @Test
  void responseSetsProblemJsonAndRetryAfterOnlyOn503() {
    ProblemDetail overloaded = problems.problem(ErrorCode.OVERLOADED, "busy");
    ProblemDetail insufficientFunds =
        problems.rejected(
            POSTING_ID, new PostingOutcome.Rejected(RejectionCode.INSUFFICIENT_FUNDS, 1));

    ResponseEntity<Object> unavailable = mapper.toResponse(overloaded);
    ResponseEntity<Object> rejected = mapper.toResponse(insufficientFunds);

    assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(unavailable.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(unavailable.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
        .isEqualTo(ApiConstants.RETRY_AFTER_SECONDS);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
  }

  @Test
  void headersCarryRetryAfterOnlyOn503() {
    HttpHeaders unavailable = mapper.headersFor(HttpStatus.SERVICE_UNAVAILABLE);
    HttpHeaders serverError = mapper.headersFor(HttpStatus.INTERNAL_SERVER_ERROR);

    assertThat(unavailable.getFirst(HttpHeaders.RETRY_AFTER))
        .isEqualTo(ApiConstants.RETRY_AFTER_SECONDS);
    assertThat(serverError.isEmpty()).isTrue();
  }
}
