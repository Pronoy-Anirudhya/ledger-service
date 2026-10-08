package com.bracits.ledgerservice.adapter.out.tigerbeetle.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.client.TigerBeetleClientFactory;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.constant.TigerBeetleConstants;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.RequestResult;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.TigerBeetleOperation;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.metrics.impl.TigerBeetleRequestMetricsImpl;
import com.bracits.ledgerservice.config.properties.TigerBeetleProperties;
import com.bracits.ledgerservice.port.out.exception.LedgerUnavailableException;
import com.tigerbeetle.Client;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FencedClientHolderImplTest {

  private static final Duration DEADLINE = Duration.ofMillis(50);
  private static final String OK = "ok";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final List<Client> created = new CopyOnWriteArrayList<>();
  private final AtomicBoolean factoryFails = new AtomicBoolean();
  private final TigerBeetleClientFactory factory = mock(TigerBeetleClientFactory.class);

  private FencedClientHolderImpl holder;

  @BeforeEach
  void stubFactory() {
    when(factory.create()).thenAnswer(invocation -> newMockClient());
  }

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void successReturnsTheResponse() {
    holder = newHolder();

    String response =
        holder.call(
            TigerBeetleOperation.LOOKUP_ACCOUNTS, client -> CompletableFuture.completedFuture(OK));

    assertThat(response).isEqualTo(OK);
    assertThat(fenceCount()).isZero();
    assertThat(created).hasSize(1);
    verify(created.getFirst(), never()).close();
    assertThat(requestCount(TigerBeetleOperation.LOOKUP_ACCOUNTS, RequestResult.SUCCESS))
        .isEqualTo(1);
  }

  @Test
  void timeoutFencesAndThrowsUnavailable() {
    holder = newHolder();
    Client old = created.getFirst();

    assertThatThrownBy(
        () ->
            holder.call(
                TigerBeetleOperation.CREATE_TRANSFERS, client -> new CompletableFuture<>()))
        .isInstanceOf(LedgerUnavailableException.class)
        .hasMessage(TigerBeetleConstants.REASON_TIMEOUT);

    assertThat(fenceCount()).isEqualTo(1);
    assertThat(created).hasSize(2);
    verify(old, timeout(2_000)).close();
    assertThat(requestCount(TigerBeetleOperation.CREATE_TRANSFERS, RequestResult.TIMEOUT))
        .isEqualTo(1);
    assertThat(usedClient()).isSameAs(created.get(1));
  }

  @Test
  void failedFutureFencesAndThrowsUnavailable() {
    holder = newHolder();
    Client old = created.getFirst();

    assertThatThrownBy(
        () ->
            holder.call(
                TigerBeetleOperation.LOOKUP_TRANSFERS,
                client -> CompletableFuture.failedFuture(new RuntimeException("evicted"))))
        .isInstanceOf(LedgerUnavailableException.class)
        .hasMessage(TigerBeetleConstants.REASON_CLIENT_ERROR);

    assertThat(fenceCount()).isEqualTo(1);
    verify(old, timeout(2_000)).close();
    assertThat(usedClient()).isSameAs(created.get(1));
  }

  @Test
  void closedClientFencesAndThrowsUnavailable() {
    holder = newHolder();
    Client old = created.getFirst();

    assertThatThrownBy(
        () ->
            holder.call(
                TigerBeetleOperation.CREATE_ACCOUNTS,
                client -> {
                  throw new IllegalStateException("closed");
                }))
        .isInstanceOf(LedgerUnavailableException.class);

    assertThat(fenceCount()).isEqualTo(1);
    verify(old, timeout(2_000)).close();
  }

  @Test
  void interruptRestoresFlagFencesAndThrowsUnavailable() {
    holder = newHolder();
    Client old = created.getFirst();
    Thread.currentThread().interrupt();

    assertThatThrownBy(
        () ->
            holder.call(
                TigerBeetleOperation.LOOKUP_ACCOUNTS, client -> new CompletableFuture<>()))
        .isInstanceOf(LedgerUnavailableException.class)
        .hasMessage(TigerBeetleConstants.REASON_INTERRUPTED);

    assertThat(Thread.interrupted()).isTrue();
    assertThat(fenceCount()).isEqualTo(1);
    verify(old, timeout(2_000)).close();
  }

  @Test
  void concurrentFailuresFenceExactlyOnce() throws Exception {
    holder = newHolder();
    Client old = created.getFirst();
    int threads = 16;
    CountDownLatch start = new CountDownLatch(1);
    // Every thread must have sent its request on the old client before any of them times out.
    CyclicBarrier allSent = new CyclicBarrier(threads);

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Throwable>> results = new CopyOnWriteArrayList<>();

      for (int i = 0; i < threads; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();

                  try {
                    holder.call(
                        TigerBeetleOperation.CREATE_TRANSFERS,
                        client -> {
                          assertThat(client).isSameAs(old);
                          awaitQuietly(allSent);
                          return new CompletableFuture<String>();
                        });
                    return null;
                  } catch (LedgerUnavailableException e) {
                    return e;
                  }
                }));
      }

      start.countDown();

      for (Future<Throwable> result : results) {
        assertThat(result.get(5, TimeUnit.SECONDS)).isInstanceOf(LedgerUnavailableException.class);
      }
    }

    assertThat(fenceCount()).isEqualTo(1);
    verify(old, timeout(2_000)).close();

    Client current = usedClient();

    assertThat(current).isNotSameAs(old);
    for (Client client : created) {
      if (client != current && client != old) {
        verify(client).close(); // lost the CAS: closed immediately
      }
    }
    verify(current, never()).close();
    verify(old, times(1)).close();
  }

  @Test
  void failedReplacementStillClosesOldClientAndRecreatesLazily() {
    holder = newHolder();
    Client old = created.getFirst();
    factoryFails.set(true);

    assertThatThrownBy(
        () ->
            holder.call(
                TigerBeetleOperation.CREATE_TRANSFERS, client -> new CompletableFuture<>()))
        .isInstanceOf(LedgerUnavailableException.class);
    verify(old, timeout(2_000)).close();

    assertThatThrownBy(
        () ->
            holder.call(
                TigerBeetleOperation.CREATE_TRANSFERS, client -> new CompletableFuture<>()))
        .isInstanceOf(LedgerUnavailableException.class)
        .hasMessage(TigerBeetleConstants.REASON_NO_CLIENT);

    factoryFails.set(false);

    assertThat(usedClient()).isSameAs(created.get(1));
  }

  @Test
  void closeClosesTheCurrentClient() {
    holder = newHolder();

    holder.close();

    verify(created.getFirst()).close();
    assertThatThrownBy(
        () ->
            holder.call(
                TigerBeetleOperation.LOOKUP_ACCOUNTS,
                client -> CompletableFuture.completedFuture(OK)))
        .isInstanceOf(LedgerUnavailableException.class);
  }

  private static void awaitQuietly(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Client newMockClient() {
    if (factoryFails.get()) {
      throw new IllegalStateException("cannot create client");
    }

    Client client = mock(Client.class);
    created.add(client);

    return client;
  }

  private FencedClientHolderImpl newHolder() {
    TigerBeetleProperties properties =
        new TigerBeetleProperties(0L, List.of("127.0.0.1:3000"), DEADLINE, 1);

    return new FencedClientHolderImpl(
        properties, factory, new TigerBeetleRequestMetricsImpl(registry));
  }

  /**
   * Makes one successful call and returns the client it used.
   */
  private Client usedClient() {
    AtomicReference<Client> used = new AtomicReference<>();

    holder.call(
        TigerBeetleOperation.LOOKUP_ACCOUNTS,
        client -> {
          used.set(client);
          return CompletableFuture.completedFuture(OK);
        });

    return used.get();
  }

  private double fenceCount() {
    return registry.get(TigerBeetleConstants.METRIC_CLIENT_FENCE).counter().count();
  }

  private long requestCount(TigerBeetleOperation operation, RequestResult result) {
    return registry
        .get(TigerBeetleConstants.METRIC_TB_REQUEST)
        .tag(TigerBeetleConstants.TAG_OPERATION, operation.tagValue())
        .tag(TigerBeetleConstants.TAG_RESULT, result.tagValue())
        .timer()
        .count();
  }
}
