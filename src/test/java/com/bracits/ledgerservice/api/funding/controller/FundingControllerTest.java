package com.bracits.ledgerservice.api.funding.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.support.ApiFixtures;
import com.bracits.ledgerservice.api.support.ApiSliceTestConfig;
import com.bracits.ledgerservice.api.support.FakeLedgerStore;
import com.bracits.ledgerservice.config.constant.ConfigConstants;
import com.bracits.ledgerservice.domain.account.enums.SystemAccount;
import com.bracits.ledgerservice.domain.constant.DomainConstants;
import com.bracits.ledgerservice.domain.posting.enums.PostingStatus;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import com.bracits.ledgerservice.domain.posting.model.Leg;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest
@Import(ApiSliceTestConfig.class)
@ActiveProfiles(ConfigConstants.TEST_PROFILE)
class FundingControllerTest {

  static final String FUNDING_BODY =
      """
          {"fundingId":"%s","accountId":"%s","amount":500000}"""
          .formatted(ApiFixtures.POSTING_ID, ApiFixtures.RECEIVER);

  @Autowired
  private MockMvc mvc;
  @Autowired
  private FakeLedgerStore store;

  @BeforeEach
  void setUp() {
    store.reset();
  }

  private ResultActions fund(String body) throws Exception {
    return mvc.perform(
        post(ApiConstants.FUNDINGS_PATH).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void fundingPostsSingleIssuanceLeg() throws Exception {
    store.answerPostings(new PostingOutcome.Posted(99L, false));

    fund(FUNDING_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.postingId").value(ApiFixtures.POSTING_ID.toString()))
        .andExpect(jsonPath("$.status").value(PostingStatus.POSTED.name()))
        .andExpect(jsonPath("$.replay").value(false))
        .andExpect(jsonPath("$.timestamp").value(99));

    assertThat(store.postings()).hasSize(1);
    assertThat(store.postings().getFirst().legs())
        .containsExactly(
            new Leg(SystemAccount.EMONEY_ISSUANCE.id(), ApiFixtures.RECEIVER, 500_000L,
                DomainConstants.FUNDING_TRANSFER_CODE));
  }

  @Test
  void fundingRejectionUsesPostingResponses() throws Exception {
    store.answerPostings(new PostingOutcome.Rejected(RejectionCode.ACCOUNT_NOT_FOUND, 1));

    fund(FUNDING_BODY)
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_NOT_FOUND.name()))
        .andExpect(jsonPath("$.postingStatus").value(PostingStatus.REJECTED.name()))
        .andExpect(jsonPath("$.legIndex").value(1));
  }

  @Test
  void zeroAmountIs400() throws Exception {
    fund(
        "{\"fundingId\":\"%s\",\"accountId\":\"%s\",\"amount\":0}".formatted(ApiFixtures.POSTING_ID,
            ApiFixtures.RECEIVER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));
  }
}
