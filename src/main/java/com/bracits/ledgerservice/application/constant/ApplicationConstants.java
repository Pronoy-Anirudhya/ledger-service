package com.bracits.ledgerservice.application.constant;

/**
 * Application-layer constants: MDC keys, metric names, tags and log templates.
 */
public final class ApplicationConstants {

  /**
   * MDC key carrying the posting id for every log line of a posting request.
   */
  public static final String MDC_POSTING_ID = "postingId";

  /**
   * Timer of the posting use case, tagged by outcome.
   */
  public static final String METRIC_POSTING_DURATION = "ledger_posting_duration";
  public static final String TAG_OUTCOME = "outcome";

  /**
   * One info line per posting. Never log request or response bodies.
   */
  public static final String LOG_POSTING_POSTED =
      "posting outcome={} replay={} legs={} durationMs={}";
  public static final String LOG_POSTING_REJECTED =
      "posting outcome={} code={} legIndex={} legs={} durationMs={}";
  public static final String LOG_POSTING_UNKNOWN =
      "posting outcome={} reason={} legs={} durationMs={}";
  public static final String LOG_POSTING_FAILED =
      "posting outcome={} exception={} legs={} durationMs={}";

  /**
   * Chart-of-accounts bootstrap: log templates.
   */
  public static final String LOG_SYSTEM_ACCOUNT_READY = "System account {} ({}): {}";
  public static final String LOG_SYSTEM_ACCOUNT_RETRY =
      "Ledger unavailable while creating system account {}; retrying in {}";

  /**
   * Chart-of-accounts bootstrap: start-up failure messages.
   */
  public static final String MSG_SYSTEM_ACCOUNT_CONFLICT =
      "System account %s (%s) already exists with different fields";
  public static final String MSG_SYSTEM_ACCOUNT_TIMEOUT =
      "Ledger unavailable for %s while creating system account %s";
  public static final String MSG_SYSTEM_ACCOUNT_INTERRUPTED =
      "Interrupted while creating system account %s";

  private ApplicationConstants() {
  }
}
