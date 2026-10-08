package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper;

import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.enums.AccountFlag;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;
import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.CreateAccountResultBatch;
import com.tigerbeetle.CreateAccountStatus;
import com.tigerbeetle.IdBatch;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Converts accounts to TigerBeetle account batches and account results back to domain types.
 * TigerBeetle types never leave this adapter package.
 */
public interface AccountBatchMapper {

  /**
   * A one-account batch.
   */
  AccountBatch toAccountBatch(Account account);

  /**
   * A one-id batch (e.g. an account id).
   */
  IdBatch toIdBatch(UUID id);

  /**
   * One status per event, in event order.
   */
  List<CreateAccountStatus> toAccountStatuses(CreateAccountResultBatch results);

  /**
   * The creation result of one account.
   */
  AccountCreation toAccountCreation(UUID accountId, AccountCreationStatus status);

  /**
   * The first account of a lookup result, or empty if none was found. Balances are converted with
   * {@code longValueExact} (D22).
   *
   * @throws LedgerErrorException if a balance does not fit in a {@code long}
   */
  Optional<Balance> toBalance(AccountBatch accounts);

  /**
   * Domain flags to TigerBeetle account bit flags.
   */
  int toAccountFlags(Set<AccountFlag> flags);
}
