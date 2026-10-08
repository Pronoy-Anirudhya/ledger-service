package com.bracits.ledgerservice.application.posting.service.impl;

import com.bracits.ledgerservice.application.constant.ApplicationConstants;
import com.bracits.ledgerservice.application.posting.metrics.PostingOutcomeRecorder;
import com.bracits.ledgerservice.application.posting.service.PostingService;
import com.bracits.ledgerservice.config.constant.ConfigConstants;
import com.bracits.ledgerservice.config.properties.PostingProperties;
import com.bracits.ledgerservice.domain.constant.DomainConstants;
import com.bracits.ledgerservice.domain.exception.DomainValidationException;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import com.bracits.ledgerservice.port.out.LedgerStore;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

/**
 * {@link PostingService} implementation: bulkhead, leg-count limit, MDC and timing, then the
 * {@link LedgerStore} port. The timer and the one log line per outcome are delegated to
 * {@link PostingOutcomeRecorder}.
 *
 * <p>Not {@code final}: {@link ConcurrencyLimit} is applied by a CGLIB subclass proxy (Spring Boot
 * proxies target classes by default), which needs a non-final class and non-final public methods (a
 * final method would run on the proxy instance, whose fields are null).
 */
@Service
public class PostingServiceImpl implements PostingService {

  private final LedgerStore ledgerStore;
  private final PostingProperties properties;
  private final PostingOutcomeRecorder recorder;

  public PostingServiceImpl(
      LedgerStore ledgerStore, PostingProperties properties, PostingOutcomeRecorder recorder) {
    this.ledgerStore = ledgerStore;
    this.properties = properties;
    this.recorder = recorder;
  }

  @Override
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
        recorder.recordFailure(posting, e, System.nanoTime() - start);
        throw e;
      }

      recorder.recordOutcome(posting, outcome, System.nanoTime() - start);
      return outcome;
    }
  }

  @Override
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
}
