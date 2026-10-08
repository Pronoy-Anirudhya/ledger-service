package com.bracits.ledgerservice.api;

/** HTTP paths, parameter names, header values and Problem Details members of the internal API. */
public final class ApiConstants {

  public static final String INTERNAL_V1 = "/internal/v1";
  public static final String ACCOUNTS_PATH = INTERNAL_V1 + "/accounts";
  public static final String POSTINGS_PATH = INTERNAL_V1 + "/postings";
  public static final String FUNDINGS_PATH = INTERNAL_V1 + "/fundings";

  public static final String PATH_VAR_ACCOUNT_ID = "accountId";
  public static final String PATH_VAR_POSTING_ID = "postingId";
  public static final String ACCOUNT_BALANCE_SUBPATH = "/{" + PATH_VAR_ACCOUNT_ID + "}/balance";
  public static final String POSTING_BY_ID_SUBPATH = "/{" + PATH_VAR_POSTING_ID + "}";

  public static final String QUERY_LEGS = "legs";

  /** {@code Retry-After} value (seconds) on every 503. */
  public static final String RETRY_AFTER_SECONDS = "1";

  /** Problem Details extension members (RFC 9457). */
  public static final String PROBLEM_CODE = "code";
  public static final String PROBLEM_POSTING_ID = "postingId";
  public static final String PROBLEM_POSTING_STATUS = "postingStatus";
  public static final String PROBLEM_LEG_INDEX = "legIndex";
  public static final String PROBLEM_ACCOUNT_ID = "accountId";
  public static final String PROBLEM_ERRORS = "errors";

  /** Problem type URIs are {@code PROBLEM_TYPE_BASE + code}, e.g. {@code urn:problem:ledger:INSUFFICIENT_FUNDS}. */
  public static final String PROBLEM_TYPE_BASE = "urn:problem:ledger:";

  /** Problem {@code detail} templates. */
  public static final String DETAIL_VALIDATION_FAILED = "The request is invalid.";
  public static final String DETAIL_POSTING_REJECTED = "Posting %s was rejected at leg %d.";
  public static final String DETAIL_POSTING_UNKNOWN =
      "The ledger did not answer in time; the outcome of posting %s is unknown. Retry with the same postingId.";
  public static final String DETAIL_LEDGER_UNAVAILABLE = "The ledger did not answer in time. Retry the request.";
  public static final String DETAIL_OVERLOADED = "Too many concurrent postings. Retry after the indicated delay.";
  public static final String DETAIL_ACCOUNT_CONFLICT = "Account %s already exists with different fields.";
  public static final String DETAIL_ACCOUNT_NOT_FOUND = "Account %s does not exist.";
  public static final String DETAIL_LEDGER_ERROR = "The ledger returned an unexpected result.";
  public static final String DETAIL_INTERNAL_ERROR = "Unexpected internal error.";

  /** Log templates of the error advice. Never log bodies. */
  public static final String LOG_REQUEST_FAILED = "request failed status={} code={} exception={}";

  private ApiConstants() {}
}
