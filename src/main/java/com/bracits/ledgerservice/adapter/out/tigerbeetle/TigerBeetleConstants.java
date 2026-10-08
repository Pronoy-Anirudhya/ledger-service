package com.bracits.ledgerservice.adapter.out.tigerbeetle;

/** TigerBeetle adapter constants: metric names and tags, health indicator name, thread names. */
public final class TigerBeetleConstants {

  /** Counter incremented every time the client is fenced (replaced). */
  public static final String METRIC_CLIENT_FENCE = "ledger_client_fence";

  /** Timer around every TigerBeetle request. */
  public static final String METRIC_TB_REQUEST = "ledger_tb_request";

  public static final String TAG_OPERATION = "operation";
  public static final String TAG_RESULT = "result";

  /** Health contributor name, used in the readiness group ({@code readinessState,tigerbeetle}). */
  public static final String HEALTH_INDICATOR_NAME = "tigerbeetle";

  /**
   * Bean name of the health indicator. Boot derives the contributor name by stripping the
   * {@code HealthIndicator} suffix, which yields {@link #HEALTH_INDICATOR_NAME}.
   */
  public static final String HEALTH_INDICATOR_BEAN_NAME = HEALTH_INDICATOR_NAME + "HealthIndicator";

  /** Health detail key carrying the failure reason. */
  public static final String HEALTH_DETAIL_ERROR = "error";

  /** Name of the virtual thread that closes a fenced client. */
  public static final String THREAD_FENCE_CLOSER = "tb-fence-closer";

  /** Separator between host and port in a replica address. */
  public static final char ADDRESS_PORT_SEPARATOR = ':';

  /** Opening bracket of an IPv6 literal in a replica address. */
  public static final char IPV6_OPEN_BRACKET = '[';

  public static final String IPV6_LITERAL_FORMAT = "[%s]";

  /** {@code timeout} of every transfer: 0 = not a pending transfer. */
  public static final int TRANSFER_TIMEOUT_NONE = 0;

  // Reasons (LedgerUnavailableException message and PostingOutcome.Unknown reason).
  public static final String REASON_TIMEOUT = "ledger request timed out";
  public static final String REASON_CLIENT_ERROR = "ledger client error";
  public static final String REASON_INTERRUPTED = "ledger request interrupted";
  public static final String REASON_NO_CLIENT = "ledger client unavailable";

  // Log message templates (never include request bodies).
  public static final String LOG_FENCED =
      "TigerBeetle client fenced after {} on operation {}: old client replaced and closing";
  public static final String LOG_FENCE_CREATE_FAILED =
      "TigerBeetle client fence: creating a replacement client failed; old client is closed and a"
          + " new one will be created on the next request";
  public static final String LOG_CLIENT_CLOSE_FAILED = "Closing a fenced TigerBeetle client failed";
  public static final String LOG_CLIENT_CREATE_FAILED =
      "Creating the TigerBeetle client failed; it will be created on the first request";
  public static final String LOG_RESULT_COUNT_MISMATCH =
      "TigerBeetle returned {} results for {} legs";
  public static final String LOG_POSTING_CONFLICT =
      "TigerBeetle reported {} on leg {}: same transfer id with different content";
  public static final String LOG_LEDGER_ERROR = "TigerBeetle reported unexpected status {} on leg {}";
  public static final String LOG_MIXED_CREATED =
      "TigerBeetle reported Created mixed with other statuses {}; verifying by lookup";
  public static final String LOG_REPLAY_NOT_FOUND =
      "Partial-exists chain: leg {} of {} not found on lookup; reporting conflict";
  public static final String LOG_HEALTH_DOWN = "TigerBeetle health check failed: {}";

  // LedgerErrorException messages.
  public static final String MSG_ACCOUNT_STATUS_UNEXPECTED = "unexpected TigerBeetle account status %s";
  public static final String MSG_ACCOUNT_RESULT_COUNT = "TigerBeetle returned %d results for 1 account";
  public static final String MSG_BALANCE_OVERFLOW = "account balance does not fit in a long";
  public static final String MSG_ADDRESS_UNRESOLVED = "cannot resolve TigerBeetle address %s";

  private TigerBeetleConstants() {}
}
