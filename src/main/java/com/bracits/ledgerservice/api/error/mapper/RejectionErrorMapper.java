package com.bracits.ledgerservice.api.error.mapper;

import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import org.springframework.http.HttpStatus;

/**
 * Maps a domain {@link RejectionCode} to its API {@link ErrorCode} and HTTP status (D19).
 */
public interface RejectionErrorMapper {

  /**
   * The error code of a rejection.
   */
  ErrorCode errorCodeOf(RejectionCode code);

  /**
   * The HTTP status of a rejection.
   */
  HttpStatus statusOf(RejectionCode code);
}
