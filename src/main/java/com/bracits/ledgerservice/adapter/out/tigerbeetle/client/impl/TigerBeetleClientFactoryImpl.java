package com.bracits.ledgerservice.adapter.out.tigerbeetle.client.impl;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.client.TigerBeetleClientFactory;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.constant.TigerBeetleConstants;
import com.bracits.ledgerservice.config.properties.TigerBeetleProperties;
import com.tigerbeetle.Client;
import com.tigerbeetle.UInt128;
import java.io.UncheckedIOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * {@link TigerBeetleClientFactory} that resolves each replica address with
 * {@link InetAddress#getByName} before creating the client (D12).
 */
@Component
public final class TigerBeetleClientFactoryImpl implements TigerBeetleClientFactory {

  private final TigerBeetleProperties properties;

  public TigerBeetleClientFactoryImpl(TigerBeetleProperties properties) {
    this.properties = properties;
  }

  @Override
  public Client create() {
    String[] addresses =
        properties.addresses().stream()
            .map(TigerBeetleClientFactoryImpl::resolve)
            .toArray(String[]::new);

    return new Client(UInt128.asBytes(properties.clusterId()), addresses);
  }

  /**
   * Resolves the host of a {@code host:port} address to an IP address (D12). A port-only address or
   * a bracketed IPv6 literal is returned unchanged.
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
      throw new UncheckedIOException(TigerBeetleConstants.MSG_ADDRESS_UNRESOLVED.formatted(host),
          e);
    }
  }
}
