package com.bracits.ledgerservice.adapter.out.tigerbeetle;

import com.bracits.ledgerservice.config.TigerBeetleProperties;
import com.bracits.ledgerservice.port.out.LedgerUnavailableException;
import com.tigerbeetle.Client;
import com.tigerbeetle.RequestException;
import com.tigerbeetle.UInt128;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.io.UncheckedIOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Holds the single shared TigerBeetle {@link Client} (spec P2) and fences it on failure (spec 8.2
 * step 4, D13).
 *
 * <p>Every request goes through {@link #call}: it waits at most the request deadline. On a timeout
 * or client error the client the request used is replaced by a new one (a CAS, so concurrent
 * failures fence once) and the old client is closed on a virtual thread, which abandons any request
 * it has not sent yet. The caller then gets a {@link LedgerUnavailableException}.
 */
@Component
public final class FencedClientHolder {

  private static final Logger log = LoggerFactory.getLogger(FencedClientHolder.class);

  /** TigerBeetle operations, used as the {@code operation} metric tag. */
  public enum Operation {
    CREATE_TRANSFERS,
    LOOKUP_TRANSFERS,
    CREATE_ACCOUNTS,
    LOOKUP_ACCOUNTS,
    HEALTH_CHECK;

    String tagValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** Request results, used as the {@code result} metric tag. */
  private enum RequestResult {
    SUCCESS,
    TIMEOUT,
    CLIENT_ERROR,
    INTERRUPTED,
    NO_CLIENT;

    String tagValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  private final AtomicReference<Client> current = new AtomicReference<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Supplier<Client> clientFactory;
  private final long deadlineNanos;
  private final MeterRegistry meterRegistry;
  private final Counter fenceCounter;
  private final Map<Operation, Map<RequestResult, Timer>> timers;

  @Autowired
  public FencedClientHolder(TigerBeetleProperties properties, MeterRegistry meterRegistry) {
    this(properties, meterRegistry, () -> newClient(properties));
  }

  FencedClientHolder(
      TigerBeetleProperties properties, MeterRegistry meterRegistry, Supplier<Client> clientFactory) {
    this.clientFactory = clientFactory;
    this.deadlineNanos = properties.requestDeadline().toNanos();
    this.meterRegistry = meterRegistry;
    this.fenceCounter = Counter.builder(TigerBeetleConstants.METRIC_CLIENT_FENCE).register(meterRegistry);
    this.timers = buildTimers(meterRegistry);
    Client initial = tryCreate();
    if (initial == null) {
      log.warn(TigerBeetleConstants.LOG_CLIENT_CREATE_FAILED);
    }
    current.set(initial);
  }

  /**
   * Sends one request on the current client and waits at most the request deadline.
   *
   * @param operation the operation, for metrics and logs
   * @param request sends the request on the given client
   * @return the response
   * @throws LedgerUnavailableException on timeout, interrupt or client error (the client is fenced)
   */
  public <T> T call(Operation operation, Function<Client, CompletableFuture<T>> request) {
    Timer.Sample sample = Timer.start(meterRegistry);
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
      sample.stop(timers.get(operation).get(result));
    }
  }

  /** Closes the current client on shutdown. */
  @PreDestroy
  public void close() {
    closed.set(true);
    Client client = current.getAndSet(null);
    if (client != null) {
      closeQuietly(client);
    }
  }

  private LedgerUnavailableException fenceAndFail(
      Client failed, Operation operation, String reason, Throwable cause) {
    fence(failed, operation, reason);
    return new LedgerUnavailableException(reason, cause);
  }

  /**
   * Replaces {@code failed} with a new client, unless another thread already did. If creating the
   * replacement fails, the slot is cleared ({@code null}) so the failed client is still closed and a
   * new one is created lazily by the next request.
   */
  private void fence(Client failed, Operation operation, String reason) {
    if (current.get() != failed) {
      return; // another thread already fenced this client
    }
    Client fresh = closed.get() ? null : tryCreate();
    if (current.compareAndSet(failed, fresh)) {
      fenceCounter.increment();
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
      return clientFactory.get();
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

  private static Map<Operation, Map<RequestResult, Timer>> buildTimers(MeterRegistry registry) {
    Map<Operation, Map<RequestResult, Timer>> byOperation = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
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

  private static Client newClient(TigerBeetleProperties properties) {
    String[] addresses =
        properties.addresses().stream().map(FencedClientHolder::resolve).toArray(String[]::new);
    return new Client(UInt128.asBytes(properties.clusterId()), addresses);
  }

  /**
   * Resolves the host of a {@code host:port} address to an IP address (D12). A port-only address
   * or a bracketed IPv6 literal is returned unchanged.
   */
  static String resolve(String address) {
    String trimmed = address.strip();
    int separator = trimmed.lastIndexOf(TigerBeetleConstants.ADDRESS_PORT_SEPARATOR);
    if (separator < 0 || trimmed.charAt(0) == TigerBeetleConstants.IPV6_OPEN_BRACKET) {
      return trimmed;
    }
    String host = trimmed.substring(0, separator);
    String portWithSeparator = trimmed.substring(separator);
    try {
      InetAddress ip = InetAddress.getByName(host);
      String hostAddress =
          ip instanceof Inet6Address
              ? TigerBeetleConstants.IPV6_LITERAL_FORMAT.formatted(ip.getHostAddress())
              : ip.getHostAddress();
      return hostAddress + portWithSeparator;
    } catch (UnknownHostException e) {
      throw new UncheckedIOException(TigerBeetleConstants.MSG_ADDRESS_UNRESOLVED.formatted(host), e);
    }
  }
}
