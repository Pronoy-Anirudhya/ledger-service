package com.bracits.ledgerservice.adapter.out.tigerbeetle.enums;

import java.util.Locale;

/**
 * TigerBeetle operations, used as the {@code operation} metric tag.
 */
public enum TigerBeetleOperation {
  CREATE_TRANSFERS,
  LOOKUP_TRANSFERS,
  CREATE_ACCOUNTS,
  LOOKUP_ACCOUNTS,
  HEALTH_CHECK;

  /**
   * The metric tag value: the lower-case constant name.
   */
  public String tagValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
