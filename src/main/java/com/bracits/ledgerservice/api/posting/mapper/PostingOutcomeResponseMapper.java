package com.bracits.ledgerservice.api.posting.mapper;

import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * Maps a {@link PostingOutcome} to its HTTP response, shared by postings and fundings (D23): Posted
 * → 200, Rejected → 422/409/500 problem, Unknown → 503 {@code LEDGER_TIMEOUT} +
 * {@code Retry-After}.
 */
public interface PostingOutcomeResponseMapper {

  /**
   * The HTTP response of the outcome of posting {@code postingId}.
   */
  ResponseEntity<Object> toResponse(UUID postingId, PostingOutcome outcome);
}
