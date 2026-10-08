package com.bracits.ledgerservice.api.error.mapper.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.error.mapper.RejectionErrorMapper;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class RejectionErrorMapperImplTest {

  private final RejectionErrorMapper mapper = new RejectionErrorMapperImpl();

  @ParameterizedTest
  @CsvSource({
      "INSUFFICIENT_FUNDS, INSUFFICIENT_FUNDS, 422",
      "ACCOUNT_NOT_FOUND, ACCOUNT_NOT_FOUND, 422",
      "PREVIOUSLY_REJECTED, PREVIOUSLY_REJECTED, 422",
      "POSTING_CONFLICT, POSTING_CONFLICT, 409",
      "LEDGER_ERROR, LEDGER_ERROR, 500"
  })
  void rejectionMapping(RejectionCode rejection, ErrorCode expectedCode, int expectedStatus) {
    assertThat(mapper.errorCodeOf(rejection)).isEqualTo(expectedCode);
    assertThat(mapper.statusOf(rejection).value()).isEqualTo(expectedStatus);
  }

  @ParameterizedTest
  @EnumSource(RejectionCode.class)
  void everyRejectionHasAnErrorCodeAndStatus(RejectionCode rejection) {
    assertThat(mapper.errorCodeOf(rejection)).isNotNull();
    assertThat(mapper.statusOf(rejection)).isNotNull();
  }
}
