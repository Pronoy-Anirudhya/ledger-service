package com.bracits.ledgerservice.api.posting.mapper.impl;

import com.bracits.ledgerservice.api.error.mapper.ProblemDetailMapper;
import com.bracits.ledgerservice.api.error.mapper.ProblemResponseMapper;
import com.bracits.ledgerservice.api.posting.mapper.PostingApiMapper;
import com.bracits.ledgerservice.api.posting.mapper.PostingOutcomeResponseMapper;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * {@link PostingOutcomeResponseMapper} implementation.
 */
@Component
public final class PostingOutcomeResponseMapperImpl implements PostingOutcomeResponseMapper {

  private final PostingApiMapper postingMapper;
  private final ProblemDetailMapper problems;
  private final ProblemResponseMapper problemResponses;

  public PostingOutcomeResponseMapperImpl(
      PostingApiMapper postingMapper,
      ProblemDetailMapper problems,
      ProblemResponseMapper problemResponses) {
    this.postingMapper = postingMapper;
    this.problems = problems;
    this.problemResponses = problemResponses;
  }

  @Override
  public ResponseEntity<Object> toResponse(UUID postingId, PostingOutcome outcome) {
    return switch (outcome) {
      case PostingOutcome.Posted posted ->
          ResponseEntity.ok(postingMapper.toResponse(postingId, posted));
      case PostingOutcome.Rejected rejected ->
          problemResponses.toResponse(problems.rejected(postingId, rejected));
      case PostingOutcome.Unknown ignored ->
          problemResponses.toResponse(problems.unknown(postingId));
    };
  }
}
