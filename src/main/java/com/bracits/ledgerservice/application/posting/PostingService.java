package com.bracits.ledgerservice.application.posting;

import com.bracits.ledgerservice.application.ApplicationConstants;
import com.bracits.ledgerservice.config.ConfigConstants;
import com.bracits.ledgerservice.config.PostingProperties;
import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.DomainValidationException;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingLookup;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.port.out.LedgerStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

/**
 * Posting use case: bulkhead, leg-count limit, MDC, timing and one log line per outcome, then the
 * {@link LedgerStore} port.
 *
 * <p>Not {@code final}: {@link ConcurrencyLimit} is applied by a CGLIB subclass proxy, which needs a
 * non-final class and non-final public methods (a final method would run on the proxy instance,
 * whose fields are null).
 */
@Service
public class PostingService {

  private static final Logger LOG = LoggerFactory.getLogger(PostingService.class);

  private final LedgerStore ledgerStore;
  private final PostingProperties properties;
  private final Map<PostingMetricOutcome, Timer> timers;

  public PostingService(LedgerStore ledgerStore, PostingProperties properties, MeterRegistry meterRegistry) {
    this.ledgerStore = ledgerStore;
    this.properties = properties;
    this.timers = new EnumMap<>(PostingMetricOutcome.class);
    for (PostingMetricOutcome outcome : PostingMetricOutcome.values()) {
      timers.put(
          outcome,
          Timer.builder(ApplicationConstants.METRIC_POSTING_DURATION)
              .tag(ApplicationConstants.TAG_OUTCOME, outcome.name())
              .register(meterRegistry));
    }
  }

  /**
   * Posts every leg atomically. When more than {@code poc.posting.concurrency-limit} postings are in
   * flight the call is rejected at once with {@link org.springframework.resilience.InvocationRejectedException}
   * (503 + {@code Retry-After}); it never queues.
   *
   * @throws DomainValidationException if the posting has more legs than {@code poc.posting.max-legs}
   */
  @ConcurrencyLimit(
      limitString = ConfigConstants.POSTING_CONCURRENCY_LIMIT_PLACEHOLDER,
      policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
  public PostingOutcome post(Posting posting) {
    requireLegCount(posting.legCount());
    try (MDC.MDCCloseable ignored =
        MDC.putCloseable(ApplicationConstants.MDC_POSTING_ID, posting.postingId().toString())) {
      long start = System.nanoTime();
      PostingOutcome outcome;
      try {
        outcome = ledgerStore.createLinked(posting);
      } catch (RuntimeException e) {
        long elapsed = System.nanoTime() - start;
        record(PostingMetricOutcome.ERROR, elapsed);
        LOG.info(
            ApplicationConstants.LOG_POSTING_FAILED,
            PostingMetricOutcome.ERROR,
            e.getClass().getSimpleName(),
            posting.legCount(),
            TimeUnit.NANOSECONDS.toMillis(elapsed));
        throw e;
      }
      long elapsed = System.nanoTime() - start;
      long durationMs = TimeUnit.NANOSECONDS.toMillis(elapsed);
      switch (outcome) {
        case PostingOutcome.Posted posted -> {
          record(PostingMetricOutcome.POSTED, elapsed);
          LOG.info(
              ApplicationConstants.LOG_POSTING_POSTED,
              PostingMetricOutcome.POSTED,
              posted.replay(),
              posting.legCount(),
              durationMs);
        }
        case PostingOutcome.Rejected rejected -> {
          record(PostingMetricOutcome.REJECTED, elapsed);
          LOG.info(
              ApplicationConstants.LOG_POSTING_REJECTED,
              PostingMetricOutcome.REJECTED,
              rejected.code(),
              rejected.legIndex(),
              posting.legCount(),
              durationMs);
        }
        case PostingOutcome.Unknown unknown -> {
          record(PostingMetricOutcome.UNKNOWN, elapsed);
          LOG.info(
              ApplicationConstants.LOG_POSTING_UNKNOWN,
              PostingMetricOutcome.UNKNOWN,
              unknown.reason(),
              posting.legCount(),
              durationMs);
        }
      }
      return outcome;
    }
  }

  /**
   * Looks up legs {@code 1..legs} of a posting.
   *
   * @throws DomainValidationException if {@code legs} is outside 1..{@code poc.posting.max-legs}
   */
  public PostingLookup lookup(UUID postingId, int legs) {
    requireLegCount(legs);
    return ledgerStore.lookup(postingId, legs);
  }

  private void requireLegCount(int legs) {
    int maxLegs = Math.min(properties.maxLegs(), DomainConstants.MAX_LEGS);
    if (legs < DomainConstants.MIN_LEGS || legs > maxLegs) {
      throw new DomainValidationException(DomainConstants.MSG_LEG_COUNT.formatted(maxLegs));
    }
  }

  private void record(PostingMetricOutcome outcome, long elapsedNanos) {
    timers.get(outcome).record(elapsedNanos, TimeUnit.NANOSECONDS);
  }
}
