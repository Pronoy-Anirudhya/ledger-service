package com.bracits.ledgerservice.api.error.mapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Maps a {@link ProblemDetail} to its HTTP response: the problem's status,
 * {@code application/problem+json}, and {@code Retry-After} on every 503.
 */
public interface ProblemResponseMapper {

  /**
   * Wraps a problem in a response with its status, content type and headers.
   */
  ResponseEntity<Object> toResponse(ProblemDetail problem);

  /**
   * {@code Retry-After} on 503, nothing otherwise.
   */
  HttpHeaders headersFor(HttpStatusCode status);
}
