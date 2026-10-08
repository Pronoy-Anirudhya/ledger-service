package com.bracits.ledgerservice.adapter.out.tigerbeetle.model;

/**
 * Result of looking up legs {@code 1..legCount} of a posting.
 *
 * @param legCount         number of legs looked up
 * @param firstMissingLeg  1-based index of the first leg not found, or {@link #NO_MISSING_LEG}
 * @param lastLegTimestamp timestamp of leg {@code legCount} when every leg was found, else 0
 */
public record LegLookup(int legCount, int firstMissingLeg, long lastLegTimestamp) {

  /**
   * {@code firstMissingLeg} value meaning every leg was found.
   */
  public static final int NO_MISSING_LEG = 0;

  public boolean allFound() {
    return firstMissingLeg == NO_MISSING_LEG;
  }
}
