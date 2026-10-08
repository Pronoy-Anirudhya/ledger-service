package com.bracits.ledgerservice.api.posting.controller;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.posting.dto.PostingLookupResponse;
import com.bracits.ledgerservice.api.posting.dto.PostingRequest;
import com.bracits.ledgerservice.api.posting.mapper.PostingApiMapper;
import com.bracits.ledgerservice.api.posting.mapper.PostingOutcomeResponseMapper;
import com.bracits.ledgerservice.application.posting.service.PostingService;
import com.bracits.ledgerservice.domain.constant.DomainConstants;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Atomic multi-leg postings and their lookup (spec 7.2).
 */
@RestController
@RequestMapping(ApiConstants.POSTINGS_PATH)
public final class PostingController {

  private final PostingService postingService;
  private final PostingApiMapper mapper;
  private final PostingOutcomeResponseMapper responseMapper;

  public PostingController(
      PostingService postingService,
      PostingApiMapper mapper,
      PostingOutcomeResponseMapper responseMapper) {
    this.postingService = postingService;
    this.mapper = mapper;
    this.responseMapper = responseMapper;
  }

  /**
   * 200 POSTED · 422 REJECTED · 409 conflict · 500 ledger error · 503 unknown or overloaded.
   */
  @PostMapping
  public ResponseEntity<Object> post(@Valid @RequestBody PostingRequest request) {
    Posting posting = mapper.toPosting(request);

    PostingOutcome outcome = postingService.post(posting);

    return responseMapper.toResponse(posting.postingId(), outcome);
  }

  /**
   * 200 POSTED (all {@code legs} legs exist) or NOT_FOUND.
   */
  @GetMapping(ApiConstants.POSTING_BY_ID_SUBPATH)
  public PostingLookupResponse lookup(
      @PathVariable(ApiConstants.PATH_VAR_POSTING_ID) UUID postingId,
      @RequestParam(ApiConstants.QUERY_LEGS)
      @Min(DomainConstants.MIN_LEGS)
      @Max(DomainConstants.MAX_LEGS)
      int legs) {
    PostingLookup lookup = postingService.lookup(postingId, legs);

    return mapper.toLookupResponse(lookup);
  }
}
