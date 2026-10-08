package com.bracits.ledgerservice.api.posting;

import static com.bracits.ledgerservice.api.support.ApiFixtures.POSTING_ID;
import static com.bracits.ledgerservice.api.support.ApiFixtures.RECEIVER;
import static com.bracits.ledgerservice.api.support.ApiFixtures.SENDER;
import static com.bracits.ledgerservice.api.support.ApiFixtures.ZERO;
import static com.bracits.ledgerservice.api.support.ApiFixtures.leg;
import static com.bracits.ledgerservice.api.support.ApiFixtures.posting;
import static com.bracits.ledgerservice.api.support.ApiFixtures.postingWithLegs;
import static com.bracits.ledgerservice.api.support.ApiFixtures.principalLeg;
import static com.bracits.ledgerservice.api.support.ApiFixtures.validPosting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.api.error.ErrorCode;
import com.bracits.ledgerservice.api.support.ApiSliceTestConfig;
import com.bracits.ledgerservice.api.support.FakeLedgerStore;
import com.bracits.ledgerservice.domain.posting.PostingLookup;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.domain.posting.PostingStatus;
import com.bracits.ledgerservice.domain.posting.RejectionCode;
import com.bracits.ledgerservice.port.out.LedgerErrorException;
import com.bracits.ledgerservice.port.out.LedgerUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest
@Import(ApiSliceTestConfig.class)
class PostingControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private FakeLedgerStore store;

  @BeforeEach
  void setUp() {
    store.reset();
  }

  private ResultActions postJson(String body) throws Exception {
    return mvc.perform(post(ApiConstants.POSTINGS_PATH).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions expectValidationProblem(ResultActions actions) throws Exception {
    return actions
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
        .andExpect(jsonPath("$.type").value(ApiConstants.PROBLEM_TYPE_BASE + ErrorCode.VALIDATION_FAILED.name()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void postedReturns200(boolean replay) throws Exception {
    store.answerPostings(new PostingOutcome.Posted(1_791_350_858_928_000_000L, replay));

    postJson(validPosting())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.postingId").value(POSTING_ID.toString()))
        .andExpect(jsonPath("$.status").value(PostingStatus.POSTED.name()))
        .andExpect(jsonPath("$.replay").value(replay))
        .andExpect(jsonPath("$.timestamp").value(1_791_350_858_928_000_000L));

    assertThat(store.postings()).hasSize(1);
    assertThat(store.postings().getFirst().legs()).hasSize(2);
    assertThat(store.postings().getFirst().legs().getFirst().debitAccountId()).isEqualTo(SENDER);
  }

  @ParameterizedTest
  @CsvSource({
    "INSUFFICIENT_FUNDS, 422, 1",
    "ACCOUNT_NOT_FOUND, 422, 2",
    "PREVIOUSLY_REJECTED, 422, 1",
    "POSTING_CONFLICT, 409, 1",
    "LEDGER_ERROR, 500, 2"
  })
  void rejectionMapsToProblem(RejectionCode code, int httpStatus, int legIndex) throws Exception {
    store.answerPostings(new PostingOutcome.Rejected(code, legIndex));

    postJson(validPosting())
        .andExpect(status().is(httpStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(httpStatus))
        .andExpect(jsonPath("$.code").value(code.name()))
        .andExpect(jsonPath("$.type").value(ApiConstants.PROBLEM_TYPE_BASE + code.name()))
        .andExpect(jsonPath("$.title").value(ErrorCode.valueOf(code.name()).title()))
        .andExpect(jsonPath("$.postingId").value(POSTING_ID.toString()))
        .andExpect(jsonPath("$.postingStatus").value(PostingStatus.REJECTED.name()))
        .andExpect(jsonPath("$.legIndex").value(legIndex))
        .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
  }

  @Test
  void unknownReturns503WithRetryAfter() throws Exception {
    store.answerPostings(new PostingOutcome.Unknown("timeout"));

    postJson(validPosting())
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, ApiConstants.RETRY_AFTER_SECONDS))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.LEDGER_TIMEOUT.name()))
        .andExpect(jsonPath("$.postingId").value(POSTING_ID.toString()))
        .andExpect(jsonPath("$.postingStatus").value(PostingStatus.UNKNOWN.name()))
        .andExpect(jsonPath("$.legIndex").doesNotExist());
  }

  @Test
  void zeroLegsIs400() throws Exception {
    expectValidationProblem(postJson(postingWithLegs(0))).andExpect(jsonPath("$.errors[0].field").value("legs"));
    assertThat(store.postings()).isEmpty();
  }

  @Test
  void nineLegsIs400() throws Exception {
    expectValidationProblem(postJson(postingWithLegs(9))).andExpect(jsonPath("$.errors[0].field").value("legs"));
    assertThat(store.postings()).isEmpty();
  }

  @Test
  void zeroAmountIs400() throws Exception {
    expectValidationProblem(postJson(posting(POSTING_ID, leg(SENDER, RECEIVER, 0, 10))))
        .andExpect(jsonPath("$.errors[0].field").value("legs[0].amount"));
  }

  @Test
  void zeroCodeIs400() throws Exception {
    expectValidationProblem(postJson(posting(POSTING_ID, leg(SENDER, RECEIVER, 100, 0))))
        .andExpect(jsonPath("$.errors[0].field").value("legs[0].code"));
  }

  @Test
  void nonZeroLowByteIs400() throws Exception {
    UUID badId = UUID.fromString("0192f5a4-0000-7000-8000-00000000ab01");
    expectValidationProblem(postJson(posting(badId, principalLeg())));
    assertThat(store.postings()).isEmpty();
  }

  @Test
  void zeroAccountIdIs400() throws Exception {
    expectValidationProblem(postJson(posting(POSTING_ID, leg(ZERO, RECEIVER, 100, 10))));
    assertThat(store.postings()).isEmpty();
  }

  @Test
  void malformedJsonIs400() throws Exception {
    expectValidationProblem(postJson("{\"postingId\":"));
  }

  @Test
  void missingBodyFieldIs400() throws Exception {
    expectValidationProblem(postJson("{\"product\":1,\"userData64\":0,\"legs\":[" + principalLeg() + "]}"))
        .andExpect(jsonPath("$.errors[0].field").value("postingId"));
  }

  @Test
  void unsupportedMediaTypeHasCode() throws Exception {
    mvc.perform(post(ApiConstants.POSTINGS_PATH).contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));
  }

  @Test
  void lookupPosted() throws Exception {
    store.answerLookups(id -> PostingLookup.posted(id, 123L));

    mvc.perform(get(ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH, POSTING_ID)
            .param(ApiConstants.QUERY_LEGS, "4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.postingId").value(POSTING_ID.toString()))
        .andExpect(jsonPath("$.status").value(PostingStatus.POSTED.name()))
        .andExpect(jsonPath("$.timestamp").value(123));
  }

  @Test
  void lookupNotFound() throws Exception {
    mvc.perform(get(ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH, POSTING_ID)
            .param(ApiConstants.QUERY_LEGS, "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PostingStatus.NOT_FOUND.name()))
        .andExpect(jsonPath("$.timestamp").doesNotExist());
  }

  @Test
  void lookupBadUuidIs400() throws Exception {
    expectValidationProblem(
        mvc.perform(get(ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH, "not-a-uuid")
            .param(ApiConstants.QUERY_LEGS, "1")));
  }

  @Test
  void lookupMissingLegsIs400() throws Exception {
    expectValidationProblem(mvc.perform(get(ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH, POSTING_ID)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "9"})
  void lookupLegsOutOfRangeIs400(String legs) throws Exception {
    expectValidationProblem(
            mvc.perform(get(ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH, POSTING_ID)
                .param(ApiConstants.QUERY_LEGS, legs)))
        .andExpect(jsonPath("$.errors[0].field").value(ApiConstants.QUERY_LEGS));
  }

  @Test
  void ledgerUnavailableOnLookupIs503() throws Exception {
    store.answerLookups(
        id -> {
          throw new LedgerUnavailableException("timeout", null);
        });

    mvc.perform(get(ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH, POSTING_ID)
            .param(ApiConstants.QUERY_LEGS, "1"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, ApiConstants.RETRY_AFTER_SECONDS))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.LEDGER_TIMEOUT.name()));
  }

  @Test
  void unexpectedExceptionIs500() throws Exception {
    store.answerPostings(
        posting -> {
          throw new IllegalStateException("boom");
        });

    postJson(validPosting())
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.name()));
  }

  @Test
  void ledgerErrorExceptionIs500LedgerError() throws Exception {
    store.answerPostings(
        posting -> {
          throw new LedgerErrorException("bad result count");
        });

    postJson(validPosting())
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(ErrorCode.LEDGER_ERROR.name()));
  }
}
