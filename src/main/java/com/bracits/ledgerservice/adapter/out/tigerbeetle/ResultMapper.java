package com.bracits.ledgerservice.adapter.out.tigerbeetle;

import com.bracits.ledgerservice.domain.account.AccountCreationStatus;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.domain.posting.RejectionCode;
import com.bracits.ledgerservice.port.out.LedgerErrorException;
import com.tigerbeetle.CreateAccountStatus;
import com.tigerbeetle.CreateTransferStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps TigerBeetle 0.17 result statuses to domain outcomes (spec 8.2, D7–D11). Pure logic over
 * plain values, so it does not depend on TigerBeetle result batches.
 */
@Component
public final class ResultMapper {

  private static final Logger log = LoggerFactory.getLogger(ResultMapper.class);

  /** Leg index reported when the failure cannot be attributed to one leg. */
  static final int UNATTRIBUTED_LEG = 1;

  /**
   * Result of one leg (one event) of a {@code create_transfers} request.
   *
   * @param status TigerBeetle status
   * @param timestamp TigerBeetle timestamp of the transfer (new or original)
   */
  public record LegResult(CreateTransferStatus status, long timestamp) {}

  /** What the store must do with a {@code create_transfers} result. */
  public sealed interface ChainResult {

    /** The outcome is decided by the statuses alone. */
    record Decided(PostingOutcome outcome) implements ChainResult {}

    /**
     * The chain returned only {@code Exists}/{@code LinkedEventFailed} (or a {@code Created} mixed
     * with others): the legs must be looked up before reporting a replay (D8).
     */
    record VerifyReplay() implements ChainResult {}
  }

  /**
   * Maps the per-leg results of one linked chain.
   *
   * @param results one result per leg, in leg order
   * @param legCount number of legs sent
   */
  public ChainResult mapTransfers(List<LegResult> results, int legCount) {
    if (results.size() != legCount || results.isEmpty()) {
      log.error(TigerBeetleConstants.LOG_RESULT_COUNT_MISMATCH, results.size(), legCount);
      return decided(new PostingOutcome.Rejected(RejectionCode.LEDGER_ERROR, UNATTRIBUTED_LEG));
    }
    long lastTimestamp = results.getLast().timestamp();
    if (allHaveStatus(results, CreateTransferStatus.Created)) {
      return decided(new PostingOutcome.Posted(lastTimestamp, false));
    }
    if (allHaveStatus(results, CreateTransferStatus.Exists)) {
      return decided(new PostingOutcome.Posted(lastTimestamp, true));
    }
    for (int i = 0; i < results.size(); i++) {
      CreateTransferStatus status = results.get(i).status();
      if (!isChainStatus(status)) {
        return decided(rejection(status, i + 1));
      }
    }
    if (results.stream().anyMatch(r -> r.status() == CreateTransferStatus.Created)) {
      log.error(TigerBeetleConstants.LOG_MIXED_CREATED, results.stream().map(LegResult::status).toList());
    }
    return new ChainResult.VerifyReplay();
  }

  /**
   * Maps the lookup that verifies a partial-exists chain (D8): every leg found → replayed POSTED
   * with the last leg's timestamp; otherwise a conflict on the first missing leg.
   */
  public PostingOutcome mapReplayVerification(TigerBeetleMapper.LegLookup legLookup) {
    if (legLookup.allFound()) {
      return new PostingOutcome.Posted(legLookup.lastLegTimestamp(), true);
    }
    log.error(
        TigerBeetleConstants.LOG_REPLAY_NOT_FOUND, legLookup.firstMissingLeg(), legLookup.legCount());
    return new PostingOutcome.Rejected(RejectionCode.POSTING_CONFLICT, legLookup.firstMissingLeg());
  }

  /**
   * Maps the result of creating one account.
   *
   * @throws LedgerErrorException if the count is not 1 or the status is unexpected
   */
  public AccountCreationStatus mapAccount(List<CreateAccountStatus> statuses) {
    if (statuses.size() != 1) {
      throw new LedgerErrorException(
          TigerBeetleConstants.MSG_ACCOUNT_RESULT_COUNT.formatted(statuses.size()));
    }
    return mapAccount(statuses.getFirst());
  }

  /**
   * Maps one account status.
   *
   * @throws LedgerErrorException if the status is not Created, Exists or ExistsWithDifferent*
   */
  public AccountCreationStatus mapAccount(CreateAccountStatus status) {
    return switch (status) {
      case Created -> AccountCreationStatus.CREATED;
      case Exists -> AccountCreationStatus.EXISTS;
      case ExistsWithDifferentFlags,
          ExistsWithDifferentUserData128,
          ExistsWithDifferentUserData64,
          ExistsWithDifferentUserData32,
          ExistsWithDifferentLedger,
          ExistsWithDifferentCode ->
          AccountCreationStatus.CONFLICT;
      default ->
          throw new LedgerErrorException(
              TigerBeetleConstants.MSG_ACCOUNT_STATUS_UNEXPECTED.formatted(status));
    };
  }

  private static PostingOutcome rejection(CreateTransferStatus status, int legIndex) {
    RejectionCode code = rejectionCode(status);
    switch (code) {
      case POSTING_CONFLICT -> log.error(TigerBeetleConstants.LOG_POSTING_CONFLICT, status, legIndex);
      case LEDGER_ERROR -> log.error(TigerBeetleConstants.LOG_LEDGER_ERROR, status, legIndex);
      case INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, PREVIOUSLY_REJECTED -> {
        // Business outcomes: no error log.
      }
    }
    return new PostingOutcome.Rejected(code, legIndex);
  }

  private static RejectionCode rejectionCode(CreateTransferStatus status) {
    return switch (status) {
      case ExceedsCredits -> RejectionCode.INSUFFICIENT_FUNDS;
      case DebitAccountNotFound, CreditAccountNotFound -> RejectionCode.ACCOUNT_NOT_FOUND;
      case ExistsWithDifferentFlags,
          ExistsWithDifferentPendingId,
          ExistsWithDifferentTimeout,
          ExistsWithDifferentDebitAccountId,
          ExistsWithDifferentCreditAccountId,
          ExistsWithDifferentAmount,
          ExistsWithDifferentUserData128,
          ExistsWithDifferentUserData64,
          ExistsWithDifferentUserData32,
          ExistsWithDifferentLedger,
          ExistsWithDifferentCode ->
          RejectionCode.POSTING_CONFLICT;
      case IdAlreadyFailed -> RejectionCode.PREVIOUSLY_REJECTED;
      default -> RejectionCode.LEDGER_ERROR;
    };
  }

  /** Statuses that are never a root cause: success, replay, or a chain-mate's failure. */
  private static boolean isChainStatus(CreateTransferStatus status) {
    return status == CreateTransferStatus.Created
        || status == CreateTransferStatus.Exists
        || status == CreateTransferStatus.LinkedEventFailed;
  }

  private static boolean allHaveStatus(List<LegResult> results, CreateTransferStatus status) {
    return results.stream().allMatch(r -> r.status() == status);
  }

  private static ChainResult decided(PostingOutcome outcome) {
    return new ChainResult.Decided(outcome);
  }
}
