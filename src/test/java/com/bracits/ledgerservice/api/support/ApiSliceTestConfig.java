package com.bracits.ledgerservice.api.support;

import com.bracits.ledgerservice.api.account.mapper.impl.AccountApiMapperImpl;
import com.bracits.ledgerservice.api.error.logging.impl.RequestFailureLoggerImpl;
import com.bracits.ledgerservice.api.error.mapper.impl.FieldViolationMapperImpl;
import com.bracits.ledgerservice.api.error.mapper.impl.ProblemDetailMapperImpl;
import com.bracits.ledgerservice.api.error.mapper.impl.ProblemResponseMapperImpl;
import com.bracits.ledgerservice.api.error.mapper.impl.RejectionErrorMapperImpl;
import com.bracits.ledgerservice.api.funding.mapper.impl.FundingApiMapperImpl;
import com.bracits.ledgerservice.api.posting.mapper.impl.PostingApiMapperImpl;
import com.bracits.ledgerservice.api.posting.mapper.impl.PostingOutcomeResponseMapperImpl;
import com.bracits.ledgerservice.application.account.service.impl.AccountServiceImpl;
import com.bracits.ledgerservice.application.posting.metrics.impl.PostingOutcomeRecorderImpl;
import com.bracits.ledgerservice.application.posting.service.impl.PostingServiceImpl;
import com.bracits.ledgerservice.config.ResilienceConfig;
import com.bracits.ledgerservice.config.properties.PostingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Wiring for {@code @WebMvcTest} slices: the application services, API mappers, error collaborators
 * and resilience config (for {@code @ConcurrencyLimit}), with the in-memory {@link FakeLedgerStore}
 * instead of TigerBeetle. Controllers and the advice are picked up by the slice itself.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(PostingProperties.class)
@Import({
    ResilienceConfig.class,
    PostingServiceImpl.class,
    PostingOutcomeRecorderImpl.class,
    AccountServiceImpl.class,
    PostingApiMapperImpl.class,
    PostingOutcomeResponseMapperImpl.class,
    AccountApiMapperImpl.class,
    FundingApiMapperImpl.class,
    ProblemDetailMapperImpl.class,
    ProblemResponseMapperImpl.class,
    RejectionErrorMapperImpl.class,
    FieldViolationMapperImpl.class,
    RequestFailureLoggerImpl.class
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
