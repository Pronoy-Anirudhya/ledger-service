package com.bracits.ledgerservice.api.posting.mapper.impl;

import com.bracits.ledgerservice.api.posting.dto.LegRequest;
import com.bracits.ledgerservice.api.posting.dto.PostingLookupResponse;
import com.bracits.ledgerservice.api.posting.dto.PostingRequest;
import com.bracits.ledgerservice.api.posting.dto.PostingResponse;
import com.bracits.ledgerservice.api.posting.mapper.PostingApiMapper;
import com.bracits.ledgerservice.domain.posting.enums.PostingStatus;
import com.bracits.ledgerservice.domain.posting.model.Leg;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link PostingApiMapper} implementation.
 */
@Component
public final class PostingApiMapperImpl implements PostingApiMapper {

  @Override
  public Posting toPosting(PostingRequest request) {
    return new Posting(
        request.postingId(),
        request.product(),
        request.userData64(),
        request.legs().stream().map(this::toLeg).toList());
  }

  @Override
  public Leg toLeg(LegRequest leg) {
    return new Leg(leg.debit(), leg.credit(), leg.amount(), leg.code());
  }

  @Override
  public PostingResponse toResponse(UUID postingId, PostingOutcome.Posted posted) {
    return new PostingResponse(
        postingId, PostingStatus.POSTED, posted.replay(), posted.ledgerTimestamp());
  }

  @Override
  public PostingLookupResponse toLookupResponse(PostingLookup lookup) {
    Long timestamp = lookup.isPosted() ? lookup.ledgerTimestamp() : null;
    return new PostingLookupResponse(lookup.postingId(), lookup.status(), timestamp);
  }
}
