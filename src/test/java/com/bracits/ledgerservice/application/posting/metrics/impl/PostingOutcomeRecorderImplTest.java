package com.bracits.ledgerservice.application.posting.metrics.impl;

import static com.bracits.ledgerservice.api.support.ApiFixtures.POSTING_ID;
import static com.bracits.ledgerservice.api.support.ApiFixtures.RECEIVER;
import static com.bracits.ledgerservice.api.support.ApiFixtures.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.application.constant.ApplicationConstants;
import com.bracits.ledgerservice.application.posting.enums.PostingMetricOutcome;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import com.bracits.ledgerservice.domain.posting.model.Leg;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PostingOutcomeRecorderImplTest {

  private static final long ELAPSED_NANOS = TimeUnit.MILLISECONDS.toNanos(7);

  private static final Posting POSTING =
      new Posting(POSTING_ID, 1, 0L, List.of(new Leg(SENDER, RECEIVER, 1L, 10)));

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final PostingOutcomeRecorderImpl recorder = new PostingOutcomeRecorderImpl(registry);

  private Timer timer(PostingMetricOutcome outcome) {
    return registry
        .get(ApplicationConstants.METRIC_POSTING_DURATION)
        .tag(ApplicationConstants.TAG_OUTCOME, outcome.name())
        .timer();
  }

  @Test
  void registersOneTimerPerOutcomeUpFront() {
    for (PostingMetricOutcome outcome : PostingMetricOutcome.values()) {
      assertThat(timer(outcome).count()).isZero();
    }
  }

  @Test
  void tagsEachOutcomeWithItsOwnTimer() {
    recorder.recordOutcome(POSTING, new PostingOutcome.Posted(1L, false), ELAPSED_NANOS);
    recorder.recordOutcome(
        POSTING, new PostingOutcome.Rejected(RejectionCode.INSUFFICIENT_FUNDS, 1), ELAPSED_NANOS);
    recorder.recordOutcome(POSTING, new PostingOutcome.Unknown("timeout"), ELAPSED_NANOS);
    recorder.recordOutcome(POSTING, new PostingOutcome.Unknown("timeout"), ELAPSED_NANOS);

    assertThat(timer(PostingMetricOutcome.POSTED).count()).isEqualTo(1);
    assertThat(timer(PostingMetricOutcome.REJECTED).count()).isEqualTo(1);
    assertThat(timer(PostingMetricOutcome.UNKNOWN).count()).isEqualTo(2);
    assertThat(timer(PostingMetricOutcome.ERROR).count()).isZero();
  }

  @Test
  void failureIsTimedUnderErrorWithTheElapsedTime() {
    recorder.recordFailure(POSTING, new IllegalStateException(), ELAPSED_NANOS);

    Timer error = timer(PostingMetricOutcome.ERROR);
    assertThat(error.count()).isEqualTo(1);
    assertThat(error.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(ELAPSED_NANOS);
    assertThat(timer(PostingMetricOutcome.POSTED).count()).isZero();
  }
}
