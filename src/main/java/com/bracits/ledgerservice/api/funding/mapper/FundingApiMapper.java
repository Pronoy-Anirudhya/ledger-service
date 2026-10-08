package com.bracits.ledgerservice.api.funding.mapper;

import com.bracits.ledgerservice.api.funding.dto.FundingRequest;
import com.bracits.ledgerservice.domain.posting.model.Posting;

/**
 * Maps a funding request to a single-leg posting (D23): e-money issuance → account, code 1, product
 * 0, {@code user_data_64} 0. The fundingId is the postingId (same low-byte rule).
 */
public interface FundingApiMapper {

  /**
   * Request → single-leg domain posting.
   */
  Posting toPosting(FundingRequest request);
}
