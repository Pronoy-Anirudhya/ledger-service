package com.bracits.ledgerservice.config.constant;

/**
 * Configuration property prefixes, placeholders, defaults and profile names.
 */
public final class ConfigConstants {

  public static final String TIGERBEETLE_PREFIX = "poc.tigerbeetle";
  public static final String POSTING_PREFIX = "poc.posting";
  public static final String ACCOUNTS_PREFIX = "poc.accounts";

  /**
   * Placeholder for {@code @ConcurrencyLimit(limitString = ...)} on the posting path.
   */
  public static final String POSTING_CONCURRENCY_LIMIT_PLACEHOLDER =
      "${poc.posting.concurrency-limit:1024}";

  /**
   * Property that enables the chart-of-accounts bootstrap.
   */
  public static final String ACCOUNTS_BOOTSTRAP_PROPERTY = "poc.accounts.bootstrap";

  public static final String DEFAULT_CLUSTER_ID = "0";
  public static final String DEFAULT_ADDRESSES = "127.0.0.1:3000";
  public static final String DEFAULT_REQUEST_DEADLINE = "800ms";
  public static final String DEFAULT_LEDGER = "1";

  public static final String DEFAULT_MAX_LEGS = "8";
  public static final String DEFAULT_CONCURRENCY_LIMIT = "1024";

  public static final String DEFAULT_BOOTSTRAP = "true";
  public static final String DEFAULT_BOOTSTRAP_TIMEOUT = "60s";
  public static final String DEFAULT_BOOTSTRAP_RETRY_INTERVAL = "1s";

  /**
   * Spring profile that enables the test-support endpoints (fundings).
   */
  public static final String TEST_PROFILE = "test";

  private ConfigConstants() {
  }
}
