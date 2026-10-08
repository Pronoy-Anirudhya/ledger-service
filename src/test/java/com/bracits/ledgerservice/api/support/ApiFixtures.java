package com.bracits.ledgerservice.api.support;

import java.util.Collections;
import java.util.UUID;

/** Request bodies and ids shared by the API tests. */
public final class ApiFixtures {

  public static final UUID POSTING_ID = UUID.fromString("0192f5a4-0000-7000-8000-00000000ab00");
  public static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000001001");
  public static final UUID RECEIVER = UUID.fromString("00000000-0000-0000-0000-000000001002");
  public static final UUID FEE_INCOME = UUID.fromString("00000000-0000-0000-0000-0000000000c8");
  public static final UUID ZERO = new UUID(0L, 0L);

  private ApiFixtures() {}

  public static String leg(UUID debit, UUID credit, long amount, int code) {
    return """
        {"debit":"%s","credit":"%s","amount":%d,"code":%d}"""
        .formatted(debit, credit, amount, code);
  }

  public static String principalLeg() {
    return leg(SENDER, RECEIVER, 100_000, 10);
  }

  public static String posting(UUID postingId, String... legs) {
    return """
        {"postingId":"%s","product":1,"userData64":42,"legs":[%s]}"""
        .formatted(postingId, String.join(",", legs));
  }

  /** A valid two-leg posting. */
  public static String validPosting() {
    return posting(POSTING_ID, principalLeg(), leg(SENDER, FEE_INCOME, 348, 11));
  }

  /** A posting with {@code count} identical principal legs. */
  public static String postingWithLegs(int count) {
    return posting(POSTING_ID, Collections.nCopies(count, principalLeg()).toArray(String[]::new));
  }
}
