package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper;

import java.util.UUID;

/**
 * Converts between domain UUIDs and TigerBeetle's 128-bit ids.
 */
public interface TigerBeetleIdMapper {

  /**
   * UUID to TigerBeetle's little-endian 128-bit id (MSB = high 64 bits).
   */
  byte[] toBytes(UUID id);

  /**
   * TigerBeetle's 128-bit id to UUID.
   */
  UUID toUuid(byte[] id);
}
