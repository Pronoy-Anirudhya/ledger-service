package com.bracits.ledgerservice.adapter.out.tigerbeetle.client;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.TigerBeetleOperation;
import com.bracits.ledgerservice.port.out.exception.LedgerUnavailableException;
import com.tigerbeetle.Client;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Holds the single shared TigerBeetle {@link Client} (spec P2) and fences it on failure (spec 8.2
 * step 4, D13).
 *
 * <p>Every request goes through {@link #call}: it waits at most the request deadline. On a timeout
 * or client error the client the request used is replaced by a new one (a CAS, so concurrent
 * failures fence once) and the old client is closed on a virtual thread, which abandons any request
 * it has not sent yet. The caller then gets a {@link LedgerUnavailableException}.
 *
 * <p>Clients are created by {@link TigerBeetleClientFactory}; request timers and the fence counter
 * live in {@code TigerBeetleRequestMetrics}.
 */
public interface FencedClientHolder {

  /**
   * Sends one request on the current client and waits at most the request deadline.
   *
   * @param operation the operation, for metrics and logs
   * @param request   sends the request on the given client
   * @return the response
   * @throws LedgerUnavailableException on timeout, interrupt or client error (the client is
   *                                    fenced)
   */
  <T> T call(TigerBeetleOperation operation, Function<Client, CompletableFuture<T>> request);
}
