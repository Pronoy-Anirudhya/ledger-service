package com.bracits.ledgerservice.api.funding.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.support.ApiSliceTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Without profile {@code test} the fundings endpoint does not exist.
 */
@WebMvcTest
@Import(ApiSliceTestConfig.class)
class FundingDisabledTest {

  @Autowired
  private MockMvc mvc;

  @Test
  void fundingsNotAvailableWithoutTestProfile() throws Exception {
    mvc.perform(post(ApiConstants.FUNDINGS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content(FundingControllerTest.FUNDING_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));
  }
}
