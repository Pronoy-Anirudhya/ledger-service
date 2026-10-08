package com.bracits.ledgerservice.adapter.out.tigerbeetle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.FencedClientHolder.Operation;
import com.bracits.ledgerservice.config.TigerBeetleProperties;
import com.bracits.ledgerservice.port.out.LedgerUnavailableException;
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
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FencedClientHolderTest {

  private static final Duration DEADLINE = Duration.ofMillis(50);
  private static final String OK = "ok";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final List<Client> created = new CopyOnWriteArrayList<>();
  private final AtomicBoolean factoryFails = new AtomicBoolean();
  private final Supplier<Client> factory =
      () -> {
        if (factoryFails.get()) {
          throw new IllegalStateException("cannot create client");
        }
        Client client = mock(Client.class);
        created.add(client);
        return client;
      };

  private FencedClientHolder holder;

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void successReturnsTheResponse() {
    holder = newHolder();

    String response = holder.call(Operation.LOOKUP_ACCOUNTS, client -> CompletableFuture.completedFuture(OK));

    assertThat(response).isEqualTo(OK);
    assertThat(fenceCount()).isZero();
    assertThat(created).hasSize(1);
    verify(created.getFirst(), never()).close();
    assertThat(requestCount(Operation.LOOKUP_ACCOUNTS, "success")).isEqualTo(1);
  }

  @Test
  void timeoutFencesAndThrowsUnavailable() {
    holder = newHolder();
    Client old = created.getFirst();

    assertThatThrownBy(() -> holder.call(Operation.CREATE_TRANSFERS, client -> new CompletableFuture<>()))
        .isInstanceOf(LedgerUnavailableException.class)
        .hasMessage(TigerBeetleConstants.REASON_TIMEOUT);

    assertThat(fenceCount()).isEqualTo(1);
    assertThat(created).hasSize(2);
    verify(old, timeout(2_000)).close();
    assertThat(requestCount(Operation.CREATE_TRANSFERS, "timeout")).isEqualTo(1);
    assertThat(usedClient()).isSameAs(created.get(1));
  }

  @Test
  void failedFutureFencesAndThrowsUnavailable() {
    holder = newHolder();
    Client old = created.getFirst();

    assertThatThrownBy(
            () ->
                holder.call(
                    Operation.LOOKUP_TRANSFERS,
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
                    Operation.CREATE_ACCOUNTS,
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

    assertThatThrownBy(() -> holder.call(Operation.LOOKUP_ACCOUNTS, client -> new CompletableFuture<>()))
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
                        Operation.CREATE_TRANSFERS,
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

    assertThatThrownBy(() -> holder.call(Operation.CREATE_TRANSFERS, client -> new CompletableFuture<>()))
        .isInstanceOf(LedgerUnavailableException.class);
    verify(old, timeout(2_000)).close();

    assertThatThrownBy(() -> holder.call(Operation.CREATE_TRANSFERS, client -> new CompletableFuture<>()))
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
    assertThatThrownBy(() -> holder.call(Operation.LOOKUP_ACCOUNTS, client -> CompletableFuture.completedFuture(OK)))
        .isInstanceOf(LedgerUnavailableException.class);
  }

  @Test
  void resolvesHostnamesToIpAddresses() {
    assertThat(FencedClientHolder.resolve("localhost:3000")).isIn("127.0.0.1:3000", "[::1]:3000");
    assertThat(FencedClientHolder.resolve("10.0.0.5:3001")).isEqualTo("10.0.0.5:3001");
    assertThat(FencedClientHolder.resolve("3000")).isEqualTo("3000");
  }

  private static void awaitQuietly(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private FencedClientHolder newHolder() {
    return new FencedClientHolder(
        new TigerBeetleProperties(0L, List.of("127.0.0.1:3000"), DEADLINE, 1), registry, factory);
  }

  /** Makes one successful call and returns the client it used. */
  private Client usedClient() {
    AtomicReference<Client> used = new AtomicReference<>();
    holder.call(
        Operation.LOOKUP_ACCOUNTS,
        client -> {
          used.set(client);
          return CompletableFuture.completedFuture(OK);
        });
    return used.get();
  }

  private double fenceCount() {
    return registry.get(TigerBeetleConstants.METRIC_CLIENT_FENCE).counter().count();
  }

  private long requestCount(Operation operation, String result) {
    return registry
        .get(TigerBeetleConstants.METRIC_TB_REQUEST)
        .tag(TigerBeetleConstants.TAG_OPERATION, operation.tagValue())
        .tag(TigerBeetleConstants.TAG_RESULT, result)
        .timer()
        .count();
  }
}
