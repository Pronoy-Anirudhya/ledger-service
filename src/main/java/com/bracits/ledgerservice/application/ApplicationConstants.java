package com.bracits.ledgerservice.application;

/** Application-layer constants: MDC keys, metric names, tags and log templates. */
public final class ApplicationConstants {

  /** MDC key carrying the posting id for every log line of a posting request. */
  public static final String MDC_POSTING_ID = "postingId";

  /** Timer of the posting use case, tagged by outcome. */
  public static final String METRIC_POSTING_DURATION = "ledger_posting_duration";

  public static final String TAG_OUTCOME = "outcome";

  /** One info line per posting. Never log request or response bodies. */
  public static final String LOG_POSTING_POSTED = "posting outcome={} replay={} legs={} durationMs={}";

  public static final String LOG_POSTING_REJECTED = "posting outcome={} code={} legIndex={} legs={} durationMs={}";

  public static final String LOG_POSTING_UNKNOWN = "posting outcome={} reason={} legs={} durationMs={}";

  public static final String LOG_POSTING_FAILED = "posting outcome={} exception={} legs={} durationMs={}";

  private ApplicationConstants() {}
}
