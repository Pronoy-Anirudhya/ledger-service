package com.bracits.ledgerservice.api.error.mapper.impl;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.mapper.ProblemResponseMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * {@link ProblemResponseMapper} implementation.
 */
@Component
public final class ProblemResponseMapperImpl implements ProblemResponseMapper {

  @Override
  public ResponseEntity<Object> toResponse(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .headers(headersFor(HttpStatus.valueOf(problem.getStatus())))
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @Override
  public HttpHeaders headersFor(HttpStatusCode status) {
    HttpHeaders headers = new HttpHeaders();

    if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
      headers.set(HttpHeaders.RETRY_AFTER, ApiConstants.RETRY_AFTER_SECONDS);
    }

    return headers;
  }
}
