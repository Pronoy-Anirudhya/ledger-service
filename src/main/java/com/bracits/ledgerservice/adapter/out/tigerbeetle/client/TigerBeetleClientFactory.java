package com.bracits.ledgerservice.adapter.out.tigerbeetle.client;

import com.bracits.ledgerservice.config.properties.TigerBeetleProperties;
import com.tigerbeetle.Client;
import java.io.UncheckedIOException;

/**
 * Creates TigerBeetle {@link Client}s from {@link TigerBeetleProperties}, resolving replica
 * hostnames to IP addresses first (D12).
 */
public interface TigerBeetleClientFactory {

  /**
   * Creates a new client for the configured cluster and replica addresses.
   *
   * @throws UncheckedIOException if a replica hostname cannot be resolved
   */
  Client create();
}
