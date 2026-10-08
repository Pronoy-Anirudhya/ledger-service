package com.bracits.ledgerservice.api.funding;

import static com.bracits.ledgerservice.api.support.ApiFixtures.POSTING_ID;
import static com.bracits.ledgerservice.api.support.ApiFixtures.RECEIVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.DomainValidationException;
import com.bracits.ledgerservice.domain.account.SystemAccount;
import com.bracits.ledgerservice.domain.posting.Leg;
import com.bracits.ledgerservice.domain.posting.Posting;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FundingApiMapperTest {

  private final FundingApiMapper mapper = new FundingApiMapper();

  @Test
  void mapsToSingleIssuanceLeg() {
    Posting posting = mapper.toPosting(new FundingRequest(POSTING_ID, RECEIVER, 500_000L));

    assertThat(posting.postingId()).isEqualTo(POSTING_ID);
    assertThat(posting.product()).isEqualTo(DomainConstants.FUNDING_PRODUCT);
    assertThat(posting.userData64()).isEqualTo(DomainConstants.FUNDING_USER_DATA_64);
    assertThat(posting.legs())
        .containsExactly(
            new Leg(SystemAccount.EMONEY_ISSUANCE.id(), RECEIVER, 500_000L, DomainConstants.FUNDING_TRANSFER_CODE));
  }

  @Test
  void fundingIdFollowsPostingIdRules() {
    UUID lowByteSet = UUID.fromString("0192f5a4-0000-7000-8000-00000000ab01");

    assertThatThrownBy(() -> mapper.toPosting(new FundingRequest(lowByteSet, RECEIVER, 1L)))
        .isInstanceOf(DomainValidationException.class);
  }
}
