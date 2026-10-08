package com.bracits.ledgerservice.application.posting.metrics;

import com.bracits.ledgerservice.application.posting.enums.PostingMetricOutcome;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;

/**
 * Records the result of one posting: the {@code ledger_posting_duration} timer tagged by
 * {@link PostingMetricOutcome}, and exactly one info log line per outcome.
 *
 * <p>Call it while the posting's MDC is still set, so the log line carries the posting id.
 */
public interface PostingOutcomeRecorder {

  /**
   * Times and logs a posting the store answered.
   */
  void recordOutcome(Posting posting, PostingOutcome outcome, long elapsedNanos);

  /**
   * Times and logs a posting whose store call threw.
   */
  void recordFailure(Posting posting, RuntimeException e, long elapsedNanos);
}
