package com.bracits.ledgerservice.api.posting;

import com.bracits.ledgerservice.api.error.ProblemDetailMapper;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link PostingOutcome} to its HTTP response, shared by postings and fundings (D23):
 * Posted → 200, Rejected → 422/409/500 problem, Unknown → 503 {@code LEDGER_TIMEOUT} + {@code
 * Retry-After}.
 */
@Component
public final class PostingOutcomeResponder {

  private final PostingApiMapper postingMapper;
  private final ProblemDetailMapper problems;

  public PostingOutcomeResponder(PostingApiMapper postingMapper, ProblemDetailMapper problems) {
    this.postingMapper = postingMapper;
    this.problems = problems;
  }

  public ResponseEntity<Object> respond(UUID postingId, PostingOutcome outcome) {
    return switch (outcome) {
      case PostingOutcome.Posted posted -> ResponseEntity.ok(postingMapper.toResponse(postingId, posted));
      case PostingOutcome.Rejected rejected -> problems.toResponse(problems.rejected(postingId, rejected));
      case PostingOutcome.Unknown ignored -> problems.toResponse(problems.unknown(postingId));
    };
  }
}
