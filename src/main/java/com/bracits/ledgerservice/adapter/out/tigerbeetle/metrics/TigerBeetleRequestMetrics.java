package com.bracits.ledgerservice.adapter.out.tigerbeetle.metrics;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.RequestResult;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.TigerBeetleOperation;
import io.micrometer.core.instrument.Timer;

/**
 * TigerBeetle client metrics: the {@code ledger_tb_request} timer per operation and result, and the
 * {@code ledger_client_fence} counter.
 */
public interface TigerBeetleRequestMetrics {

  /**
   * Starts timing one request.
   */
  Timer.Sample startRequest();

  /**
   * Stops timing one request and records it under its operation and result.
   */
  void recordRequest(Timer.Sample sample, TigerBeetleOperation operation, RequestResult result);

  /**
   * Counts one fence (client replaced).
   */
  void recordFence();
}
