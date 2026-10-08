package com.bracits.ledgerservice.api.posting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.support.ApiFixtures;
import com.bracits.ledgerservice.api.support.ApiSliceTestConfig;
import com.bracits.ledgerservice.api.support.FakeLedgerStore;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * P10: with the posting bulkhead full, the next posting gets 503 OVERLOADED at once.
 */
@WebMvcTest(properties = "poc.posting.concurrency-limit=1")
@Import(ApiSliceTestConfig.class)
class PostingSaturationTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  private FakeLedgerStore store;

  @BeforeEach
  void setUp() {
    store.reset();
  }

  @AfterEach
  void tearDown() {
    store.release();
  }

  private MvcResult postValid() throws Exception {
    return mvc.perform(post(ApiConstants.POSTINGS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content(ApiFixtures.validPosting()))
        .andReturn();
  }

  @Test
  void secondConcurrentPostingIsRejectedImmediately() throws Exception {
    CountDownLatch entered = store.blockPostings();

    CompletableFuture<MvcResult> first =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return postValid();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });

    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    long start = System.nanoTime();

    mvc.perform(post(ApiConstants.POSTINGS_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content(ApiFixtures.validPosting()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, ApiConstants.RETRY_AFTER_SECONDS))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.OVERLOADED.name()))
        .andExpect(
            jsonPath("$.type").value(ApiConstants.PROBLEM_TYPE_BASE + ErrorCode.OVERLOADED.name()));

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    assertThat(first).isNotDone();

    store.release();

    assertThat(first.get(5, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
    assertThat(store.postings()).hasSize(1);

    // the permit is returned: the next posting succeeds
    assertThat(postValid().getResponse().getStatus()).isEqualTo(200);
  }
}
