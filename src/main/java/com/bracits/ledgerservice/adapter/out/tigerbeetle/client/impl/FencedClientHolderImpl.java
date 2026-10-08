package com.bracits.ledgerservice.adapter.out.tigerbeetle.client.impl;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.client.FencedClientHolder;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.client.TigerBeetleClientFactory;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.constant.TigerBeetleConstants;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.RequestResult;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.TigerBeetleOperation;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.metrics.TigerBeetleRequestMetrics;
import com.bracits.ledgerservice.config.properties.TigerBeetleProperties;
import com.bracits.ledgerservice.port.out.exception.LedgerUnavailableException;
import com.tigerbeetle.Client;
import com.tigerbeetle.RequestException;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link FencedClientHolder} that keeps the current client in an {@link AtomicReference} and
 * replaces it with a CAS on failure. Clients are created by {@link TigerBeetleClientFactory};
 * request timers and the fence counter live in {@link TigerBeetleRequestMetrics}.
 */
@Component
public final class FencedClientHolderImpl implements FencedClientHolder {

  private static final Logger log = LoggerFactory.getLogger(FencedClientHolderImpl.class);

  private final AtomicReference<Client> current = new AtomicReference<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final TigerBeetleClientFactory clientFactory;
  private final TigerBeetleRequestMetrics metrics;
  private final long deadlineNanos;

  public FencedClientHolderImpl(
      TigerBeetleProperties properties,
      TigerBeetleClientFactory clientFactory,
      TigerBeetleRequestMetrics metrics) {
    this.clientFactory = clientFactory;
    this.metrics = metrics;
    this.deadlineNanos = properties.requestDeadline().toNanos();

    Client initial = tryCreate();

    if (initial == null) {
      log.warn(TigerBeetleConstants.LOG_CLIENT_CREATE_FAILED);
    }

    current.set(initial);
  }

  @Override
  public <T> T call(
      TigerBeetleOperation operation, Function<Client, CompletableFuture<T>> request) {
    Timer.Sample sample = metrics.startRequest();
    RequestResult result = RequestResult.CLIENT_ERROR;

    try {
      Client client = currentClient();

      if (client == null) {
        result = RequestResult.NO_CLIENT;
        throw new LedgerUnavailableException(TigerBeetleConstants.REASON_NO_CLIENT, null);
      }

      try {
        T response = request.apply(client).get(deadlineNanos, TimeUnit.NANOSECONDS);
        result = RequestResult.SUCCESS;

        return response;
      } catch (TimeoutException e) {
        result = RequestResult.TIMEOUT;
        throw fenceAndFail(client, operation, TigerBeetleConstants.REASON_TIMEOUT, e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        result = RequestResult.INTERRUPTED;
        throw fenceAndFail(client, operation, TigerBeetleConstants.REASON_INTERRUPTED, e);
      } catch (ExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        throw fenceAndFail(client, operation, TigerBeetleConstants.REASON_CLIENT_ERROR, cause);
      } catch (RequestException | IllegalStateException e) {
        // IllegalStateException covers a closed client and a cancelled future.
        throw fenceAndFail(client, operation, TigerBeetleConstants.REASON_CLIENT_ERROR, e);
      }
    } finally {
      metrics.recordRequest(sample, operation, result);
    }
  }

  /**
   * Closes the current client on shutdown.
   */
  @PreDestroy
  public void close() {
    closed.set(true);
    Client client = current.getAndSet(null);

    if (client != null) {
      closeQuietly(client);
    }
  }

  private LedgerUnavailableException fenceAndFail(
      Client failed, TigerBeetleOperation operation, String reason, Throwable cause) {
    fence(failed, operation, reason);

    return new LedgerUnavailableException(reason, cause);
  }

  /**
   * Replaces {@code failed} with a new client, unless another thread already did. If creating the
   * replacement fails, the slot is cleared ({@code null}) so the failed client is still closed and
   * a new one is created lazily by the next request.
   */
  private void fence(Client failed, TigerBeetleOperation operation, String reason) {
    if (current.get() != failed) {
      return; // another thread already fenced this client
    }

    Client fresh = closed.get() ? null : tryCreate();

    if (current.compareAndSet(failed, fresh)) {
      metrics.recordFence();

      if (fresh == null) {
        log.error(TigerBeetleConstants.LOG_FENCE_CREATE_FAILED);
      } else {
        log.warn(TigerBeetleConstants.LOG_FENCED, reason, operation.tagValue());
      }

      Thread.ofVirtual()
          .name(TigerBeetleConstants.THREAD_FENCE_CLOSER)
          .start(() -> closeQuietly(failed));
    } else if (fresh != null) {
      closeQuietly(fresh); // lost the race: another thread already installed a new client
    }
  }

  private Client currentClient() {
    Client client = current.get();

    if (client != null || closed.get()) {
      return client;
    }

    Client fresh = tryCreate();

    if (fresh == null) {
      return null;
    }

    if (current.compareAndSet(null, fresh)) {
      return fresh;
    }

    closeQuietly(fresh);

    return current.get();
  }

  private Client tryCreate() {
    try {
      return clientFactory.create();
    } catch (RuntimeException e) {
      log.warn(TigerBeetleConstants.LOG_CLIENT_CREATE_FAILED, e);
      return null;
    }
  }

  private static void closeQuietly(Client client) {
    try {
      client.close();
    } catch (RuntimeException e) {
      log.warn(TigerBeetleConstants.LOG_CLIENT_CLOSE_FAILED, e);
    }
  }
}
