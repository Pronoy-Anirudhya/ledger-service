package com.bracits.ledgerservice.api.funding.controller;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.funding.dto.FundingRequest;
import com.bracits.ledgerservice.api.funding.mapper.FundingApiMapper;
import com.bracits.ledgerservice.api.posting.mapper.PostingOutcomeResponseMapper;
import com.bracits.ledgerservice.application.posting.service.PostingService;
import com.bracits.ledgerservice.config.constant.ConfigConstants;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only cash-in from the e-money issuance account (profile {@code test}, D23).
 */
@RestController
@Profile(ConfigConstants.TEST_PROFILE)
@RequestMapping(ApiConstants.FUNDINGS_PATH)
public final class FundingController {

  private final PostingService postingService;
  private final FundingApiMapper mapper;
  private final PostingOutcomeResponseMapper responseMapper;

  public FundingController(
      PostingService postingService,
      FundingApiMapper mapper,
      PostingOutcomeResponseMapper responseMapper) {
    this.postingService = postingService;
    this.mapper = mapper;
    this.responseMapper = responseMapper;
  }

  /**
   * Same responses as {@code POST /internal/v1/postings}.
   */
  @PostMapping
  public ResponseEntity<Object> fund(@Valid @RequestBody FundingRequest request) {
    Posting posting = mapper.toPosting(request);

    PostingOutcome outcome = postingService.post(posting);

    return responseMapper.toResponse(posting.postingId(), outcome);
  }
}
