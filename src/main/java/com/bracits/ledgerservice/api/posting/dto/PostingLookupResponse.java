package com.bracits.ledgerservice.api.posting.dto;

import com.bracits.ledgerservice.domain.posting.enums.PostingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * {@code GET /internal/v1/postings/{postingId}?legs=n} body.
 *
 * @param postingId the posting
 * @param status    POSTED or NOT_FOUND
 * @param timestamp TigerBeetle timestamp (ns) when POSTED; absent otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostingLookupResponse(UUID postingId, PostingStatus status, Long timestamp) {

}
