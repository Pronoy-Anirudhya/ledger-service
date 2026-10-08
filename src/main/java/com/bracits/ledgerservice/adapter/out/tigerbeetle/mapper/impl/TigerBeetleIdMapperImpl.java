package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.TigerBeetleIdMapper;
import com.tigerbeetle.UInt128;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link TigerBeetleIdMapper} on TigerBeetle's {@link UInt128} helpers.
 */
@Component
public final class TigerBeetleIdMapperImpl implements TigerBeetleIdMapper {

  @Override
  public byte[] toBytes(UUID id) {
    return UInt128.asBytes(id);
  }

  @Override
  public UUID toUuid(byte[] id) {
    return UInt128.asUUID(id);
  }
}
