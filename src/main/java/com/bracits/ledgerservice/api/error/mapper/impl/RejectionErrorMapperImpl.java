package com.bracits.ledgerservice.api.error.mapper.impl;

import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.error.mapper.RejectionErrorMapper;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * {@link RejectionErrorMapper} implementation.
 */
@Component
public final class RejectionErrorMapperImpl implements RejectionErrorMapper {

  @Override
  public ErrorCode errorCodeOf(RejectionCode code) {
    return switch (code) {
      case INSUFFICIENT_FUNDS -> ErrorCode.INSUFFICIENT_FUNDS;
      case ACCOUNT_NOT_FOUND -> ErrorCode.ACCOUNT_NOT_FOUND;
      case PREVIOUSLY_REJECTED -> ErrorCode.PREVIOUSLY_REJECTED;
      case POSTING_CONFLICT -> ErrorCode.POSTING_CONFLICT;
      case LEDGER_ERROR -> ErrorCode.LEDGER_ERROR;
    };
  }

  @Override
  public HttpStatus statusOf(RejectionCode code) {
    return switch (code) {
      case INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, PREVIOUSLY_REJECTED ->
          HttpStatus.UNPROCESSABLE_CONTENT;
      case POSTING_CONFLICT -> HttpStatus.CONFLICT;
      case LEDGER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
