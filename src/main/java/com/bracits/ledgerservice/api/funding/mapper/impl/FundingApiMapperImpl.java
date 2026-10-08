package com.bracits.ledgerservice.api.funding.mapper.impl;

import com.bracits.ledgerservice.api.funding.dto.FundingRequest;
import com.bracits.ledgerservice.api.funding.mapper.FundingApiMapper;
import com.bracits.ledgerservice.domain.account.enums.SystemAccount;
import com.bracits.ledgerservice.domain.constant.DomainConstants;
import com.bracits.ledgerservice.domain.posting.model.Leg;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link FundingApiMapper} implementation.
 */
@Component
public final class FundingApiMapperImpl implements FundingApiMapper {

  @Override
  public Posting toPosting(FundingRequest request) {
    Leg leg =
        new Leg(
            SystemAccount.EMONEY_ISSUANCE.id(),
            request.accountId(),
            request.amount(),
            DomainConstants.FUNDING_TRANSFER_CODE);

    return new Posting(
        request.fundingId(),
        DomainConstants.FUNDING_PRODUCT,
        DomainConstants.FUNDING_USER_DATA_64,
        List.of(leg));
  }
}
