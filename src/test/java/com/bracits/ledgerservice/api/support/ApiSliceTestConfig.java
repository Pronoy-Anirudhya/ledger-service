package com.bracits.ledgerservice.api.support;

import com.bracits.ledgerservice.api.account.AccountApiMapper;
import com.bracits.ledgerservice.api.error.ProblemDetailMapper;
import com.bracits.ledgerservice.api.funding.FundingApiMapper;
import com.bracits.ledgerservice.api.posting.PostingApiMapper;
import com.bracits.ledgerservice.api.posting.PostingOutcomeResponder;
import com.bracits.ledgerservice.application.account.AccountService;
import com.bracits.ledgerservice.application.posting.PostingService;
import com.bracits.ledgerservice.config.PostingProperties;
import com.bracits.ledgerservice.config.ResilienceConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Wiring for {@code @WebMvcTest} slices: the application services, API mappers and resilience
 * config (for {@code @ConcurrencyLimit}), with the in-memory {@link FakeLedgerStore} instead of
 * TigerBeetle. Controllers and the advice are picked up by the slice itself.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostingProperties.class)
@Import({
  ResilienceConfig.class,
  PostingService.class,
  AccountService.class,
  PostingApiMapper.class,
  PostingOutcomeResponder.class,
  AccountApiMapper.class,
  FundingApiMapper.class,
  ProblemDetailMapper.class
})
public class ApiSliceTestConfig {

  @Bean
  FakeLedgerStore fakeLedgerStore() {
    return new FakeLedgerStore();
  }

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }
}
