package com.bracits.ledgerservice.api.funding;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.api.posting.PostingOutcomeResponder;
import com.bracits.ledgerservice.application.posting.PostingService;
import com.bracits.ledgerservice.config.ConfigConstants;
import com.bracits.ledgerservice.domain.posting.Posting;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only cash-in from the e-money issuance account (profile {@code test}, D23). */
@RestController
@Profile(ConfigConstants.TEST_PROFILE)
@RequestMapping(ApiConstants.FUNDINGS_PATH)
public final class FundingController {

  private final PostingService postingService;
  private final FundingApiMapper mapper;
  private final PostingOutcomeResponder responder;

  public FundingController(PostingService postingService, FundingApiMapper mapper, PostingOutcomeResponder responder) {
    this.postingService = postingService;
    this.mapper = mapper;
    this.responder = responder;
  }

  /** Same responses as {@code POST /internal/v1/postings}. */
  @PostMapping
  public ResponseEntity<Object> fund(@Valid @RequestBody FundingRequest request) {
    Posting posting = mapper.toPosting(request);
    return responder.respond(posting.postingId(), postingService.post(posting));
  }
}
