package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import static com.tigerbeetle.CreateTransferStatus.Created;
import static com.tigerbeetle.CreateTransferStatus.CreditAccountNotFound;
import static com.tigerbeetle.CreateTransferStatus.DebitAccountNotFound;
import static com.tigerbeetle.CreateTransferStatus.ExceedsCredits;
import static com.tigerbeetle.CreateTransferStatus.Exists;
import static com.tigerbeetle.CreateTransferStatus.IdAlreadyFailed;
import static com.tigerbeetle.CreateTransferStatus.LinkedEventFailed;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.ChainResult;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegLookup;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegResult;
import com.bracits.ledgerservice.domain.posting.enums.RejectionCode;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import com.tigerbeetle.CreateTransferStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class TransferResultMapperImplTest {

  private final TransferResultMapperImpl mapper = new TransferResultMapperImpl();

  @Test
  void allCreatedIsPostedWithLastLegTimestamp() {
    List<LegResult> results = List.of(leg(Created, 100), leg(Created, 101), leg(Created, 102));

    assertThat(mapper.mapTransfers(results, 3))
        .isEqualTo(decided(new PostingOutcome.Posted(102, false)));
  }

  @Test
  void allExistsIsReplayWithLastLegTimestamp() {
    List<LegResult> results = List.of(leg(Exists, 50), leg(Exists, 51));

    assertThat(mapper.mapTransfers(results, 2))
        .isEqualTo(decided(new PostingOutcome.Posted(51, true)));
  }

  @Test
  void singleLegCreated() {
    assertThat(mapper.mapTransfers(List.of(leg(Created, 7)), 1))
        .isEqualTo(decided(new PostingOutcome.Posted(7, false)));
  }

  @Test
  void exceedsCreditsOnFirstLegIsInsufficientFunds() {
    List<LegResult> results =
        List.of(leg(ExceedsCredits, 0), leg(LinkedEventFailed, 0), leg(LinkedEventFailed, 0));

    assertThat(mapper.mapTransfers(results, 3))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.INSUFFICIENT_FUNDS, 1)));
  }

  @Test
  void rootCauseIsSelectedPastLinkedEventFailed() {
    List<LegResult> results =
        List.of(
            leg(LinkedEventFailed, 0),
            leg(ExceedsCredits, 0),
            leg(LinkedEventFailed, 0),
            leg(LinkedEventFailed, 0));

    assertThat(mapper.mapTransfers(results, 4))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.INSUFFICIENT_FUNDS, 2)));
  }

  @Test
  void firstRootCauseWins() {
    List<LegResult> results =
        List.of(leg(LinkedEventFailed, 0), leg(CreditAccountNotFound, 0), leg(ExceedsCredits, 0));

    assertThat(mapper.mapTransfers(results, 3))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.ACCOUNT_NOT_FOUND, 2)));
  }

  @ParameterizedTest
  @EnumSource(names = {"DebitAccountNotFound", "CreditAccountNotFound"})
  void accountNotFound(CreateTransferStatus status) {
    List<LegResult> results =
        List.of(leg(LinkedEventFailed, 0), leg(LinkedEventFailed, 0), leg(status, 0));

    assertThat(mapper.mapTransfers(results, 3))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.ACCOUNT_NOT_FOUND, 3)));
  }

  @ParameterizedTest
  @MethodSource("existsWithDifferentStatuses")
  void existsWithDifferentIsPostingConflict(CreateTransferStatus status) {
    List<LegResult> results = List.of(leg(Exists, 1), leg(status, 0));

    assertThat(mapper.mapTransfers(results, 2))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.POSTING_CONFLICT, 2)));
  }

  @Test
  void idAlreadyFailedIsPreviouslyRejected() {
    List<LegResult> results = List.of(leg(IdAlreadyFailed, 0), leg(LinkedEventFailed, 0));

    assertThat(mapper.mapTransfers(results, 2))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.PREVIOUSLY_REJECTED, 1)));
  }

  @ParameterizedTest
  @MethodSource("otherStatuses")
  void anyOtherStatusIsLedgerError(CreateTransferStatus status) {
    List<LegResult> results = List.of(leg(LinkedEventFailed, 0), leg(status, 0));

    assertThat(mapper.mapTransfers(results, 2))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.LEDGER_ERROR, 2)));
  }

  @Test
  void resultCountMismatchIsLedgerError() {
    List<LegResult> results = List.of(leg(Created, 1), leg(Created, 2));

    assertThat(mapper.mapTransfers(results, 3))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.LEDGER_ERROR, 1)));
  }

  @Test
  void emptyResultsIsLedgerError() {
    assertThat(mapper.mapTransfers(List.of(), 1))
        .isEqualTo(decided(new PostingOutcome.Rejected(RejectionCode.LEDGER_ERROR, 1)));
  }

  @Test
  void partialExistsChainAsksForVerification() {
    List<LegResult> results = List.of(leg(Exists, 10), leg(LinkedEventFailed, 0), leg(Exists, 12));

    assertThat(mapper.mapTransfers(results, 3)).isInstanceOf(ChainResult.VerifyReplay.class);
  }

  @Test
  void onlyLinkedEventFailedAsksForVerification() {
    List<LegResult> results = List.of(leg(LinkedEventFailed, 0), leg(LinkedEventFailed, 0));

    assertThat(mapper.mapTransfers(results, 2)).isInstanceOf(ChainResult.VerifyReplay.class);
  }

  @Test
  void createdMixedWithExistsAsksForVerification() {
    List<LegResult> results = List.of(leg(Created, 10), leg(Exists, 11));

    assertThat(mapper.mapTransfers(results, 2)).isInstanceOf(ChainResult.VerifyReplay.class);
  }

  @Test
  void replayVerificationAllFoundIsReplay() {
    LegLookup lookup = new LegLookup(3, LegLookup.NO_MISSING_LEG, 99);

    assertThat(mapper.mapReplayVerification(lookup)).isEqualTo(new PostingOutcome.Posted(99, true));
  }

  @Test
  void replayVerificationMissingLegIsConflict() {
    LegLookup lookup = new LegLookup(3, 2, 0);

    assertThat(mapper.mapReplayVerification(lookup))
        .isEqualTo(new PostingOutcome.Rejected(RejectionCode.POSTING_CONFLICT, 2));
  }

  static Stream<CreateTransferStatus> existsWithDifferentStatuses() {
    return Arrays.stream(CreateTransferStatus.values())
        .filter(s -> s.name().startsWith("ExistsWithDifferent"));
  }

  static Stream<CreateTransferStatus> otherStatuses() {
    List<CreateTransferStatus> mapped =
        new ArrayList<>(
            List.of(
                Created,
                Exists,
                LinkedEventFailed,
                ExceedsCredits,
                DebitAccountNotFound,
                CreditAccountNotFound,
                IdAlreadyFailed));

    return Arrays.stream(CreateTransferStatus.values())
        .filter(s -> !mapped.contains(s))
        .filter(s -> !s.name().startsWith("ExistsWithDifferent"));
  }

  private static LegResult leg(CreateTransferStatus status, long timestamp) {
    return new LegResult(status, timestamp);
  }

  private static ChainResult decided(PostingOutcome outcome) {
    return new ChainResult.Decided(outcome);
  }
}
