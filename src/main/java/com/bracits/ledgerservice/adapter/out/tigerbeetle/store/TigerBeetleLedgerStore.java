package com.bracits.ledgerservice.adapter.out.tigerbeetle.store;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.client.FencedClientHolder;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.enums.TigerBeetleOperation;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.AccountBatchMapper;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.AccountResultMapper;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.TransferBatchMapper;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.TransferResultMapper;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.ChainResult;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegLookup;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.model.LegResult;
import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import com.bracits.ledgerservice.port.out.LedgerStore;
import com.bracits.ledgerservice.port.out.exception.LedgerUnavailableException;
import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.CreateAccountResultBatch;
import com.tigerbeetle.CreateAccountStatus;
import com.tigerbeetle.CreateTransferResultBatch;
import com.tigerbeetle.IdBatch;
import com.tigerbeetle.TransferBatch;
import java.util.List;
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
  private final TransferBatchMapper transferBatchMapper;
  private final AccountBatchMapper accountBatchMapper;
  private final TransferResultMapper transferResultMapper;
  private final AccountResultMapper accountResultMapper;

  public TigerBeetleLedgerStore(
      FencedClientHolder clientHolder,
      TransferBatchMapper transferBatchMapper,
      AccountBatchMapper accountBatchMapper,
      TransferResultMapper transferResultMapper,
      AccountResultMapper accountResultMapper) {
    this.clientHolder = clientHolder;
    this.transferBatchMapper = transferBatchMapper;
    this.accountBatchMapper = accountBatchMapper;
    this.transferResultMapper = transferResultMapper;
    this.accountResultMapper = accountResultMapper;
  }

  @Override
  public PostingOutcome createLinked(Posting posting) {
    TransferBatch batch = transferBatchMapper.toTransferBatch(posting);

    try {
      CreateTransferResultBatch results =
          clientHolder.call(
              TigerBeetleOperation.CREATE_TRANSFERS, client -> client.createTransfersAsync(batch));
      List<LegResult> legResults = transferBatchMapper.toLegResults(results);

      return switch (transferResultMapper.mapTransfers(legResults, posting.legCount())) {
        case ChainResult.Decided decided -> decided.outcome();
        case ChainResult.VerifyReplay _ -> transferResultMapper.mapReplayVerification(
            lookupLegs(posting.postingId(), posting.legCount()));
      };
    } catch (LedgerUnavailableException e) {
      return new PostingOutcome.Unknown(e.getMessage());
    }
  }

  @Override
  public PostingLookup lookup(UUID postingId, int legCount) {
    LegLookup legLookup = lookupLegs(postingId, legCount);

    return transferBatchMapper.toPostingLookup(postingId, legLookup);
  }

  @Override
  public AccountCreation createAccount(Account account) {
    AccountBatch batch = accountBatchMapper.toAccountBatch(account);

    CreateAccountResultBatch results =
        clientHolder.call(
            TigerBeetleOperation.CREATE_ACCOUNTS, client -> client.createAccountsAsync(batch));

    List<CreateAccountStatus> statuses = accountBatchMapper.toAccountStatuses(results);
    AccountCreationStatus status = accountResultMapper.mapAccount(statuses);

    return accountBatchMapper.toAccountCreation(account.accountId(), status);
  }

  @Override
  public Optional<Balance> balance(UUID accountId) {
    IdBatch ids = accountBatchMapper.toIdBatch(accountId);

    AccountBatch accounts =
        clientHolder.call(
            TigerBeetleOperation.LOOKUP_ACCOUNTS, client -> client.lookupAccountsAsync(ids));

    return accountBatchMapper.toBalance(accounts);
  }

  private LegLookup lookupLegs(UUID postingId, int legCount) {
    IdBatch ids = transferBatchMapper.toLegIdBatch(postingId, legCount);

    TransferBatch found =
        clientHolder.call(
            TigerBeetleOperation.LOOKUP_TRANSFERS, client -> client.lookupTransfersAsync(ids));

    return transferBatchMapper.toLegLookup(postingId, legCount, found);
  }
}
