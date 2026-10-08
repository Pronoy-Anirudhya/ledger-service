package com.bracits.ledgerservice.adapter.out.tigerbeetle.enums;

import java.util.Locale;

/**
 * Results of a TigerBeetle request, used as the {@code result} metric tag.
 */
public enum RequestResult {
  SUCCESS,
  TIMEOUT,
  CLIENT_ERROR,
  INTERRUPTED,
  NO_CLIENT;

  /**
   * The metric tag value: the lower-case constant name.
   */
  public String tagValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
