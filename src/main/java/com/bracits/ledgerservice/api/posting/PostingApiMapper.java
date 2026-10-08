package com.bracits.ledgerservice.api.posting;

import com.bracits.ledgerservice.domain.posting.Leg;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingLookup;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.domain.posting.PostingStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps posting requests to the domain and posting results to response bodies. */
@Component
public final class PostingApiMapper {

  /**
   * Request → domain. The request is already bean-validated (non-null fields); the domain
   * constructors enforce the remaining invariants and throw {@code DomainValidationException}.
   */
  public Posting toPosting(PostingRequest request) {
    return new Posting(
        request.postingId(),
        request.product(),
        request.userData64(),
        request.legs().stream().map(this::toLeg).toList());
  }

  /** Leg request → domain leg. */
  public Leg toLeg(LegRequest leg) {
    return new Leg(leg.debit(), leg.credit(), leg.amount(), leg.code());
  }

  /** 200 POSTED body. */
  public PostingResponse toResponse(UUID postingId, PostingOutcome.Posted posted) {
    return new PostingResponse(postingId, PostingStatus.POSTED, posted.replay(), posted.ledgerTimestamp());
  }

  /** Lookup body; the timestamp is present only when POSTED. */
  public PostingLookupResponse toLookupResponse(PostingLookup lookup) {
    Long timestamp = lookup.isPosted() ? lookup.ledgerTimestamp() : null;
    return new PostingLookupResponse(lookup.postingId(), lookup.status(), timestamp);
  }
}
