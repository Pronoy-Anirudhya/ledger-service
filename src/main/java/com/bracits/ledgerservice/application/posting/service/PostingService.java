package com.bracits.ledgerservice.application.posting.service;

import com.bracits.ledgerservice.domain.exception.DomainValidationException;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import java.util.UUID;

/**
 * Posting use case: post a balanced multi-leg posting atomically, and look one up.
 */
public interface PostingService {

  /**
   * Posts every leg atomically. When more than {@code poc.posting.concurrency-limit} postings are
   * in flight the call is rejected at once with
   * {@link org.springframework.resilience.InvocationRejectedException} (503 + {@code Retry-After});
   * it never queues.
   *
   * @throws DomainValidationException if the posting has more legs than
   *                                   {@code poc.posting.max-legs}
   */
  PostingOutcome post(Posting posting);

  /**
   * Looks up legs {@code 1..legs} of a posting.
   *
   * @throws DomainValidationException if {@code legs} is outside 1..{@code poc.posting.max-legs}
   */
  PostingLookup lookup(UUID postingId, int legs);
}
