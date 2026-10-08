package com.bracits.ledgerservice.api.posting;

import static com.bracits.ledgerservice.api.support.ApiFixtures.FEE_INCOME;
import static com.bracits.ledgerservice.api.support.ApiFixtures.POSTING_ID;
import static com.bracits.ledgerservice.api.support.ApiFixtures.RECEIVER;
import static com.bracits.ledgerservice.api.support.ApiFixtures.SENDER;
import static com.bracits.ledgerservice.api.support.ApiFixtures.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.ledgerservice.domain.DomainValidationException;
import com.bracits.ledgerservice.domain.posting.Leg;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingLookup;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.domain.posting.PostingStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostingApiMapperTest {

  private final PostingApiMapper mapper = new PostingApiMapper();

  @Test
  void mapsRequestToPostingKeepingLegOrder() {
    PostingRequest request =
        new PostingRequest(
            POSTING_ID,
            1,
            42L,
            List.of(new LegRequest(SENDER, RECEIVER, 100_000L, 10), new LegRequest(SENDER, FEE_INCOME, 348L, 11)));

    Posting posting = mapper.toPosting(request);

    assertThat(posting.postingId()).isEqualTo(POSTING_ID);
    assertThat(posting.product()).isEqualTo(1);
    assertThat(posting.userData64()).isEqualTo(42L);
    assertThat(posting.legs())
        .containsExactly(new Leg(SENDER, RECEIVER, 100_000L, 10), new Leg(SENDER, FEE_INCOME, 348L, 11));
  }

  @Test
  void domainInvariantsAreEnforced() {
    PostingRequest zeroAccount =
        new PostingRequest(POSTING_ID, 1, 0L, List.of(new LegRequest(ZERO, RECEIVER, 1L, 10)));
    PostingRequest lowByte =
        new PostingRequest(
            UUID.fromString("0192f5a4-0000-7000-8000-00000000ab07"),
            1,
            0L,
            List.of(new LegRequest(SENDER, RECEIVER, 1L, 10)));

    assertThatThrownBy(() -> mapper.toPosting(zeroAccount)).isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> mapper.toPosting(lowByte)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void mapsPostedToResponse() {
    PostingResponse response = mapper.toResponse(POSTING_ID, new PostingOutcome.Posted(77L, true));

    assertThat(response).isEqualTo(new PostingResponse(POSTING_ID, PostingStatus.POSTED, true, 77L));
  }

  @Test
  void lookupTimestampOnlyWhenPosted() {
    assertThat(mapper.toLookupResponse(PostingLookup.posted(POSTING_ID, 5L)))
        .isEqualTo(new PostingLookupResponse(POSTING_ID, PostingStatus.POSTED, 5L));
    assertThat(mapper.toLookupResponse(PostingLookup.notFound(POSTING_ID)))
        .isEqualTo(new PostingLookupResponse(POSTING_ID, PostingStatus.NOT_FOUND, null));
  }
}
