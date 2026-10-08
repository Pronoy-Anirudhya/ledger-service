package com.bracits.ledgerservice.api.posting;

import com.bracits.ledgerservice.domain.posting.PostingStatus;
import java.util.UUID;

/**
 * 200 POSTED body.
 *
 * @param postingId the posting
 * @param status always POSTED
 * @param replay true if the posting already existed
 * @param timestamp TigerBeetle timestamp (ns) of the posting
 */
public record PostingResponse(UUID postingId, PostingStatus status, boolean replay, long timestamp) {}
