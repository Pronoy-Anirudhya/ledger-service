package com.bracits.ledgerservice.application.posting.metrics.impl;

import com.bracits.ledgerservice.application.constant.ApplicationConstants;
import com.bracits.ledgerservice.application.posting.enums.PostingMetricOutcome;
import com.bracits.ledgerservice.application.posting.metrics.PostingOutcomeRecorder;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Micrometer and SLF4J implementation of {@link PostingOutcomeRecorder}: one timer per
 * {@link PostingMetricOutcome}, registered up front.
 */
@Component
public final class PostingOutcomeRecorderImpl implements PostingOutcomeRecorder {

  private static final Logger LOG = LoggerFactory.getLogger(PostingOutcomeRecorderImpl.class);

  private final Map<PostingMetricOutcome, Timer> timers;

  public PostingOutcomeRecorderImpl(MeterRegistry meterRegistry) {
    this.timers = new EnumMap<>(PostingMetricOutcome.class);
    for (PostingMetricOutcome outcome : PostingMetricOutcome.values()) {
      timers.put(
          outcome,
          Timer.builder(ApplicationConstants.METRIC_POSTING_DURATION)
              .tag(ApplicationConstants.TAG_OUTCOME, outcome.name())
              .register(meterRegistry));
    }
  }

  @Override
  public void recordOutcome(Posting posting, PostingOutcome outcome, long elapsedNanos) {
    long durationMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

    switch (outcome) {
      case PostingOutcome.Posted posted -> {
        record(PostingMetricOutcome.POSTED, elapsedNanos);
        LOG.info(
            ApplicationConstants.LOG_POSTING_POSTED,
            PostingMetricOutcome.POSTED,
            posted.replay(),
            posting.legCount(),
            durationMs);
      }
      case PostingOutcome.Rejected rejected -> {
        record(PostingMetricOutcome.REJECTED, elapsedNanos);
        LOG.info(
            ApplicationConstants.LOG_POSTING_REJECTED,
            PostingMetricOutcome.REJECTED,
            rejected.code(),
            rejected.legIndex(),
            posting.legCount(),
            durationMs);
      }
      case PostingOutcome.Unknown unknown -> {
        record(PostingMetricOutcome.UNKNOWN, elapsedNanos);
        LOG.info(
            ApplicationConstants.LOG_POSTING_UNKNOWN,
            PostingMetricOutcome.UNKNOWN,
            unknown.reason(),
            posting.legCount(),
            durationMs);
      }
    }
  }

  @Override
  public void recordFailure(Posting posting, RuntimeException e, long elapsedNanos) {
    record(PostingMetricOutcome.ERROR, elapsedNanos);

    LOG.info(
        ApplicationConstants.LOG_POSTING_FAILED,
        PostingMetricOutcome.ERROR,
        e.getClass().getSimpleName(),
        posting.legCount(),
        TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
  }

  private void record(PostingMetricOutcome outcome, long elapsedNanos) {
    timers.get(outcome).record(elapsedNanos, TimeUnit.NANOSECONDS);
  }
}
