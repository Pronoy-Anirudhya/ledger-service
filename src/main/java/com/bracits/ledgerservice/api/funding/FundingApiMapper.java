package com.bracits.ledgerservice.api.funding;

import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.account.SystemAccount;
import com.bracits.ledgerservice.domain.posting.Leg;
import com.bracits.ledgerservice.domain.posting.Posting;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps a funding request to a single-leg posting (D23): e-money issuance → account, code 1,
 * product 0, {@code user_data_64} 0. The fundingId is the postingId (same low-byte rule).
 */
@Component
public final class FundingApiMapper {

  public Posting toPosting(FundingRequest request) {
    Leg leg =
        new Leg(
            SystemAccount.EMONEY_ISSUANCE.id(),
            request.accountId(),
            request.amount(),
            DomainConstants.FUNDING_TRANSFER_CODE);
    return new Posting(
        request.fundingId(), DomainConstants.FUNDING_PRODUCT, DomainConstants.FUNDING_USER_DATA_64, List.of(leg));
  }
}
