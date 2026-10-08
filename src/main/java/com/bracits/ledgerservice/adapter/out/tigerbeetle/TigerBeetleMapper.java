package com.bracits.ledgerservice.adapter.out.tigerbeetle;

import com.bracits.ledgerservice.config.TigerBeetleProperties;
import com.bracits.ledgerservice.domain.account.Account;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import com.bracits.ledgerservice.domain.account.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.AccountFlag;
import com.bracits.ledgerservice.domain.account.Balance;
import com.bracits.ledgerservice.domain.posting.Leg;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingLookup;
import com.bracits.ledgerservice.domain.posting.TransferIds;
import com.bracits.ledgerservice.port.out.LedgerErrorException;
import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.AccountFlags;
import com.tigerbeetle.CreateAccountResultBatch;
import com.tigerbeetle.CreateAccountStatus;
import com.tigerbeetle.CreateTransferResultBatch;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.TransferBatch;
import com.tigerbeetle.TransferFlags;
import com.tigerbeetle.UInt128;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Converts between domain types and TigerBeetle types. TigerBeetle types never leave this adapter
 * package.
 */
@Component
public final class TigerBeetleMapper {

  /** {@code firstMissingLeg} value meaning every leg was found. */
  static final int NO_MISSING_LEG = 0;

  /**
   * Result of looking up legs {@code 1..legCount} of a posting.
   *
   * @param legCount number of legs looked up
   * @param firstMissingLeg 1-based index of the first leg not found, or {@link #NO_MISSING_LEG}
   * @param lastLegTimestamp timestamp of leg {@code legCount} when every leg was found, else 0
   */
  public record LegLookup(int legCount, int firstMissingLeg, long lastLegTimestamp) {

    public boolean allFound() {
      return firstMissingLeg == NO_MISSING_LEG;
    }
  }

  private final int ledger;

  public TigerBeetleMapper(TigerBeetleProperties properties) {
    this.ledger = properties.ledger();
  }

  /**
   * One row per leg: id = {@code postingId | n} (n 1-based), {@code LINKED} on every leg but the
   * last, {@code user_data_128} = postingId, {@code user_data_64} and {@code user_data_32} from the
   * posting, {@code timeout} = 0.
   */
  public TransferBatch toTransferBatch(Posting posting) {
    List<Leg> legs = posting.legs();
    int legCount = legs.size();
    byte[] postingId = toBytes(posting.postingId());
    TransferBatch batch = new TransferBatch(legCount);
    for (int i = 0; i < legCount; i++) {
      Leg leg = legs.get(i);
      int legIndex = i + 1;
      batch.add();
      batch.setId(toBytes(TransferIds.legId(posting.postingId(), legIndex)));
      batch.setDebitAccountId(toBytes(leg.debitAccountId()));
      batch.setCreditAccountId(toBytes(leg.creditAccountId()));
      batch.setAmount(BigInteger.valueOf(leg.amount()));
      batch.setUserData128(postingId);
      batch.setUserData64(posting.userData64());
      batch.setUserData32(posting.product());
      batch.setTimeout(TigerBeetleConstants.TRANSFER_TIMEOUT_NONE);
      batch.setLedger(ledger);
      batch.setCode(leg.code());
      batch.setFlags(legIndex < legCount ? TransferFlags.LINKED : TransferFlags.NONE);
    }
    return batch;
  }

  /** A one-account batch. */
  public AccountBatch toAccountBatch(Account account) {
    AccountBatch batch = new AccountBatch(1);
    batch.add();
    batch.setId(toBytes(account.accountId()));
    batch.setLedger(ledger);
    batch.setCode(account.code());
    batch.setUserData64(account.userData64());
    batch.setFlags(toAccountFlags(account.flags()));
    return batch;
  }

  /** The ids of legs {@code 1..legCount} of a posting. */
  public IdBatch toLegIdBatch(UUID postingId, int legCount) {
    byte[][] ids = new byte[legCount][];
    for (int n = 1; n <= legCount; n++) {
      ids[n - 1] = toBytes(TransferIds.legId(postingId, n));
    }
    return new IdBatch(ids);
  }

  /** A one-id batch (e.g. an account id). */
  public IdBatch toIdBatch(UUID id) {
    return new IdBatch(toBytes(id));
  }

  /** One (status, timestamp) per event, in event order. */
  public List<ResultMapper.LegResult> toLegResults(CreateTransferResultBatch results) {
    List<ResultMapper.LegResult> legResults = new ArrayList<>(results.getLength());
    results.beforeFirst();
    while (results.next()) {
      legResults.add(new ResultMapper.LegResult(results.getStatus(), results.getTimestamp()));
    }
    return legResults;
  }

  /** One status per event, in event order. */
  public List<CreateAccountStatus> toAccountStatuses(CreateAccountResultBatch results) {
    List<CreateAccountStatus> statuses = new ArrayList<>(results.getLength());
    results.beforeFirst();
    while (results.next()) {
      statuses.add(results.getStatus());
    }
    return statuses;
  }

  /** Checks which of legs {@code 1..legCount} are among the transfers found. */
  public LegLookup toLegLookup(UUID postingId, int legCount, TransferBatch found) {
    Map<UUID, Long> timestampsById = new HashMap<>();
    found.beforeFirst();
    while (found.next()) {
      timestampsById.put(toUuid(found.getId()), found.getTimestamp());
    }
    for (int n = 1; n <= legCount; n++) {
      if (!timestampsById.containsKey(TransferIds.legId(postingId, n))) {
        return new LegLookup(legCount, n, 0L);
      }
    }
    return new LegLookup(
        legCount, NO_MISSING_LEG, timestampsById.get(TransferIds.legId(postingId, legCount)));
  }

  /** POSTED with the last leg's timestamp only if every leg was found, otherwise NOT_FOUND (D24). */
  public PostingLookup toPostingLookup(UUID postingId, LegLookup legLookup) {
    return legLookup.allFound()
        ? PostingLookup.posted(postingId, legLookup.lastLegTimestamp())
        : PostingLookup.notFound(postingId);
  }

  public AccountCreation toAccountCreation(UUID accountId, AccountCreationStatus status) {
    return new AccountCreation(accountId, status);
  }

  /** The first account of a lookup result, or empty if none was found. */
  public Optional<Balance> toBalance(AccountBatch accounts) {
    accounts.beforeFirst();
    if (!accounts.next()) {
      return Optional.empty();
    }
    return Optional.of(
        toBalance(
            toUuid(accounts.getId()),
            accounts.getDebitsPosted(),
            accounts.getCreditsPosted(),
            accounts.getDebitsPending(),
            accounts.getCreditsPending()));
  }

  /**
   * Converts TigerBeetle's 128-bit balances with {@code longValueExact} (D22).
   *
   * @throws LedgerErrorException if a balance does not fit in a {@code long}
   */
  Balance toBalance(
      UUID accountId,
      BigInteger debitsPosted,
      BigInteger creditsPosted,
      BigInteger debitsPending,
      BigInteger creditsPending) {
    try {
      return new Balance(
          accountId,
          debitsPosted.longValueExact(),
          creditsPosted.longValueExact(),
          debitsPending.longValueExact(),
          creditsPending.longValueExact());
    } catch (ArithmeticException e) {
      throw new LedgerErrorException(TigerBeetleConstants.MSG_BALANCE_OVERFLOW);
    }
  }

  /** Domain flags to TigerBeetle account bit flags. */
  public int toAccountFlags(Set<AccountFlag> flags) {
    int bits = AccountFlags.NONE;
    for (AccountFlag flag : flags) {
      bits |= toAccountFlag(flag);
    }
    return bits;
  }

  /** UUID to TigerBeetle's little-endian 128-bit id (MSB = high 64 bits). */
  public byte[] toBytes(UUID id) {
    return UInt128.asBytes(id);
  }

  /** TigerBeetle's 128-bit id to UUID. */
  public UUID toUuid(byte[] id) {
    return UInt128.asUUID(id);
  }

  private static int toAccountFlag(AccountFlag flag) {
    return switch (flag) {
      case DEBITS_MUST_NOT_EXCEED_CREDITS -> AccountFlags.DEBITS_MUST_NOT_EXCEED_CREDITS;
      case CREDITS_MUST_NOT_EXCEED_DEBITS -> AccountFlags.CREDITS_MUST_NOT_EXCEED_DEBITS;
      case HISTORY -> AccountFlags.HISTORY;
    };
  }
}
