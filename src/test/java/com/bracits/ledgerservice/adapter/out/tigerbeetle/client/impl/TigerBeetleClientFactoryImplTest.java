package com.bracits.ledgerservice.adapter.out.tigerbeetle.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TigerBeetleClientFactoryImplTest {

  @Test
  void resolvesHostnamesToIpAddresses() {
    assertThat(TigerBeetleClientFactoryImpl.resolve("localhost:3000"))
        .isIn("127.0.0.1:3000", "[::1]:3000");
    assertThat(TigerBeetleClientFactoryImpl.resolve("10.0.0.5:3001")).isEqualTo("10.0.0.5:3001");
    assertThat(TigerBeetleClientFactoryImpl.resolve("3000")).isEqualTo("3000");
  }
}
