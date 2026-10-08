package com.bracits.ledgerservice.adapter.out.tigerbeetle;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.FencedClientHolder.Operation;
import com.bracits.ledgerservice.domain.account.Account;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import com.bracits.ledgerservice.domain.account.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.Balance;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingLookup;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.port.out.LedgerStore;
import com.bracits.ledgerservice.port.out.LedgerUnavailableException;
import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.CreateAccountResultBatch;
import com.tigerbeetle.CreateTransferResultBatch;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.TransferBatch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link LedgerStore} adapter on TigerBeetle. One posting = one small {@link TransferBatch} = one
 * {@code createTransfersAsync} (spec P4: the client auto-batches concurrent requests).
 */
@Component
public final class TigerBeetleLedgerStore implements LedgerStore {

  private final FencedClientHolder clientHolder;
  private final TigerBeetleMapper mapper;
  private final ResultMapper resultMapper;

  public TigerBeetleLedgerStore(
      FencedClientHolder clientHolder, TigerBeetleMapper mapper, ResultMapper resultMapper) {
    this.clientHolder = clientHolder;
    this.mapper = mapper;
    this.resultMapper = resultMapper;
  }

  @Override
  public PostingOutcome createLinked(Posting posting) {
    TransferBatch batch = mapper.toTransferBatch(posting);
    try {
      CreateTransferResultBatch results =
          clientHolder.call(Operation.CREATE_TRANSFERS, client -> client.createTransfersAsync(batch));
      return switch (resultMapper.mapTransfers(mapper.toLegResults(results), posting.legCount())) {
        case ResultMapper.ChainResult.Decided decided -> decided.outcome();
        case ResultMapper.ChainResult.VerifyReplay _ ->
            resultMapper.mapReplayVerification(lookupLegs(posting.postingId(), posting.legCount()));
      };
    } catch (LedgerUnavailableException e) {
      return new PostingOutcome.Unknown(e.getMessage());
    }
  }

  @Override
  public PostingLookup lookup(UUID postingId, int legCount) {
    return mapper.toPostingLookup(postingId, lookupLegs(postingId, legCount));
  }

  @Override
  public AccountCreation createAccount(Account account) {
    AccountBatch batch = mapper.toAccountBatch(account);
    CreateAccountResultBatch results =
        clientHolder.call(Operation.CREATE_ACCOUNTS, client -> client.createAccountsAsync(batch));
    AccountCreationStatus status = resultMapper.mapAccount(mapper.toAccountStatuses(results));
    return mapper.toAccountCreation(account.accountId(), status);
  }

  @Override
  public Optional<Balance> balance(UUID accountId) {
    IdBatch ids = mapper.toIdBatch(accountId);
    AccountBatch accounts =
        clientHolder.call(Operation.LOOKUP_ACCOUNTS, client -> client.lookupAccountsAsync(ids));
    return mapper.toBalance(accounts);
  }

  private TigerBeetleMapper.LegLookup lookupLegs(UUID postingId, int legCount) {
    IdBatch ids = mapper.toLegIdBatch(postingId, legCount);
    TransferBatch found =
        clientHolder.call(Operation.LOOKUP_TRANSFERS, client -> client.lookupTransfersAsync(ids));
    return mapper.toLegLookup(postingId, legCount, found);
  }
}
