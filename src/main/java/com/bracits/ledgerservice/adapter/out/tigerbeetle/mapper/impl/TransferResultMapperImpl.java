package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.constant.TigerBeetleConstants;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.TransferResultMapper;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.ChainResult;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegLookup;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegResult;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import com.tigerbeetle.CreateTransferStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link TransferResultMapper} that logs ledger errors and conflicts but not business rejections.
 */
@Component
public final class TransferResultMapperImpl implements TransferResultMapper {

  private static final Logger log = LoggerFactory.getLogger(TransferResultMapperImpl.class);

  /**
   * Leg index reported when the failure cannot be attributed to one leg.
   */
  static final int UNATTRIBUTED_LEG = 1;

  @Override
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
      log.error(
          TigerBeetleConstants.LOG_MIXED_CREATED,
          results.stream().map(LegResult::status).toList());
    }

    return new ChainResult.VerifyReplay();
  }

  @Override
  public PostingOutcome mapReplayVerification(LegLookup legLookup) {
    if (legLookup.allFound()) {
      return new PostingOutcome.Posted(legLookup.lastLegTimestamp(), true);
    }

    log.error(
        TigerBeetleConstants.LOG_REPLAY_NOT_FOUND,
        legLookup.firstMissingLeg(),
        legLookup.legCount());

    return new PostingOutcome.Rejected(RejectionCode.POSTING_CONFLICT, legLookup.firstMissingLeg());
  }

  private static PostingOutcome rejection(CreateTransferStatus status, int legIndex) {
    RejectionCode code = rejectionCode(status);

    switch (code) {
      case POSTING_CONFLICT ->
          log.error(TigerBeetleConstants.LOG_POSTING_CONFLICT, status, legIndex);
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
           ExistsWithDifferentCode -> RejectionCode.POSTING_CONFLICT;
      case IdAlreadyFailed -> RejectionCode.PREVIOUSLY_REJECTED;
      default -> RejectionCode.LEDGER_ERROR;
    };
  }

  /**
   * Statuses that are never a root cause: success, replay, or a chain-mate's failure.
   */
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
