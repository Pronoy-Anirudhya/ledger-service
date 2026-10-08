package com.bracits.ledgerservice.domain.posting.model;

import com.bracits.ledgerservice.domain.posting.enums.PostingStatus;
import java.util.UUID;

/**
 * Result of looking up a posting's legs.
 *
 * @param postingId       the posting
 * @param status          {@link PostingStatus#POSTED} if every leg exists, otherwise
 *                        {@link PostingStatus#NOT_FOUND}
 * @param ledgerTimestamp timestamp (ns) of the last leg when posted; 0 otherwise
 */
public record PostingLookup(UUID postingId, PostingStatus status, long ledgerTimestamp) {

  public static PostingLookup posted(UUID postingId, long ledgerTimestamp) {
    return new PostingLookup(postingId, PostingStatus.POSTED, ledgerTimestamp);
  }

  public static PostingLookup notFound(UUID postingId) {
    return new PostingLookup(postingId, PostingStatus.NOT_FOUND, 0L);
  }

  public boolean isPosted() {
    return status == PostingStatus.POSTED;
  }
}
