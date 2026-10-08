package com.bracits.ledgerservice.application.posting;

import static com.bracits.ledgerservice.api.support.ApiFixtures.POSTING_ID;
import static com.bracits.ledgerservice.api.support.ApiFixtures.RECEIVER;
import static com.bracits.ledgerservice.api.support.ApiFixtures.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.ledgerservice.api.support.FakeLedgerStore;
import com.bracits.ledgerservice.application.ApplicationConstants;
import com.bracits.ledgerservice.config.PostingProperties;
import com.bracits.ledgerservice.domain.DomainValidationException;
import com.bracits.ledgerservice.domain.posting.Leg;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.domain.posting.RejectionCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class PostingServiceTest {

  private static final int MAX_LEGS = 4;

  private final FakeLedgerStore store = new FakeLedgerStore();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final PostingService service = new PostingService(store, new PostingProperties(MAX_LEGS, 1), registry);

  private static Posting postingWithLegs(int count) {
    return new Posting(POSTING_ID, 1, 0L, Collections.nCopies(count, new Leg(SENDER, RECEIVER, 1L, 10)));
  }

  @Test
  void delegatesAndTimesByOutcome() {
    store.answerPostings(new PostingOutcome.Rejected(RejectionCode.INSUFFICIENT_FUNDS, 1));

    PostingOutcome outcome = service.post(postingWithLegs(2));

    assertThat(outcome).isEqualTo(new PostingOutcome.Rejected(RejectionCode.INSUFFICIENT_FUNDS, 1));
    assertThat(
            registry
                .get(ApplicationConstants.METRIC_POSTING_DURATION)
                .tag(ApplicationConstants.TAG_OUTCOME, PostingMetricOutcome.REJECTED.name())
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void postingIdIsInMdcDuringTheCallOnly() {
    AtomicReference<String> seen = new AtomicReference<>();
    store.answerPostings(
        posting -> {
          seen.set(MDC.get(ApplicationConstants.MDC_POSTING_ID));
          return new PostingOutcome.Posted(1L, false);
        });

    service.post(postingWithLegs(1));

    assertThat(seen.get()).isEqualTo(POSTING_ID.toString());
    assertThat(MDC.get(ApplicationConstants.MDC_POSTING_ID)).isNull();
  }

  @Test
  void enforcesConfiguredMaxLegs() {
    assertThatThrownBy(() -> service.post(postingWithLegs(MAX_LEGS + 1))).isInstanceOf(DomainValidationException.class);
    assertThat(store.postings()).isEmpty();
  }

  @Test
  void lookupLegsWithinConfiguredRange() {
    assertThatThrownBy(() -> service.lookup(POSTING_ID, 0)).isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> service.lookup(POSTING_ID, MAX_LEGS + 1)).isInstanceOf(DomainValidationException.class);
    assertThat(service.lookup(POSTING_ID, MAX_LEGS).isPosted()).isFalse();
  }
}
