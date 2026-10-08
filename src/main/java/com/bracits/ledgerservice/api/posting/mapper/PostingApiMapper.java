package com.bracits.ledgerservice.api.posting.mapper;

import com.bracits.ledgerservice.api.posting.dto.LegRequest;
import com.bracits.ledgerservice.api.posting.dto.PostingLookupResponse;
import com.bracits.ledgerservice.api.posting.dto.PostingRequest;
import com.bracits.ledgerservice.api.posting.dto.PostingResponse;
import com.bracits.ledgerservice.domain.posting.model.Leg;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.util.UUID;

/**
 * Maps posting requests to the domain and posting results to response bodies.
 */
public interface PostingApiMapper {

  /**
   * Request → domain. The request is already bean-validated (non-null fields); the domain
   * constructors enforce the remaining invariants and throw {@code DomainValidationException}.
   */
  Posting toPosting(PostingRequest request);

  /**
   * Leg request → domain leg.
   */
  Leg toLeg(LegRequest leg);

  /**
   * 200 POSTED body.
   */
  PostingResponse toResponse(UUID postingId, PostingOutcome.Posted posted);

  /**
   * Lookup body; the timestamp is present only when POSTED.
   */
  PostingLookupResponse toLookupResponse(PostingLookup lookup);
}
