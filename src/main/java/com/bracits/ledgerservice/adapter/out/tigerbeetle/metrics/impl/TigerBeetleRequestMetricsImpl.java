package com.bracits.ledgerservice.adapter.out.tigerbeetle.metrics.impl;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.constant.TigerBeetleConstants;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.RequestResult;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.TigerBeetleOperation;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.metrics.TigerBeetleRequestMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link TigerBeetleRequestMetrics} on Micrometer. Every meter is registered up front.
 */
@Component
public final class TigerBeetleRequestMetricsImpl implements TigerBeetleRequestMetrics {

  private final MeterRegistry meterRegistry;
  private final Counter fenceCounter;
  private final Map<TigerBeetleOperation, Map<RequestResult, Timer>> timers;

  public TigerBeetleRequestMetricsImpl(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.fenceCounter =
        Counter.builder(TigerBeetleConstants.METRIC_CLIENT_FENCE).register(meterRegistry);
    this.timers = buildTimers(meterRegistry);
  }

  @Override
  public Timer.Sample startRequest() {
    return Timer.start(meterRegistry);
  }

  @Override
  public void recordRequest(
      Timer.Sample sample, TigerBeetleOperation operation, RequestResult result) {
    sample.stop(timers.get(operation).get(result));
  }

  @Override
  public void recordFence() {
    fenceCounter.increment();
  }

  private static Map<TigerBeetleOperation, Map<RequestResult, Timer>> buildTimers(
      MeterRegistry registry) {
    Map<TigerBeetleOperation, Map<RequestResult, Timer>> byOperation =
        new EnumMap<>(TigerBeetleOperation.class);

    for (TigerBeetleOperation operation : TigerBeetleOperation.values()) {
      Map<RequestResult, Timer> byResult = new EnumMap<>(RequestResult.class);

      for (RequestResult result : RequestResult.values()) {
        byResult.put(
            result,
            Timer.builder(TigerBeetleConstants.METRIC_TB_REQUEST)
                .tag(TigerBeetleConstants.TAG_OPERATION, operation.tagValue())
                .tag(TigerBeetleConstants.TAG_RESULT, result.tagValue())
                .register(registry));
      }

      byOperation.put(operation, byResult);
    }

    return byOperation;
  }
}
