package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.constant.TigerBeetleConstants;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.AccountBatchMapper;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.TigerBeetleIdMapper;
import com.bracits.ledgerservice.config.properties.TigerBeetleProperties;
import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.enums.AccountFlag;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;
import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.tigerbeetle.AccountBatch;
import com.tigerbeetle.AccountFlags;
import com.tigerbeetle.CreateAccountResultBatch;
import com.tigerbeetle.CreateAccountStatus;
import com.tigerbeetle.IdBatch;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link AccountBatchMapper} for the configured ledger, with ids converted by
 * {@link TigerBeetleIdMapper}.
 */
@Component
public final class AccountBatchMapperImpl implements AccountBatchMapper {

  private final int ledger;
  private final TigerBeetleIdMapper idMapper;

  public AccountBatchMapperImpl(TigerBeetleProperties properties, TigerBeetleIdMapper idMapper) {
    this.ledger = properties.ledger();
    this.idMapper = idMapper;
  }

  @Override
  public AccountBatch toAccountBatch(Account account) {
    AccountBatch batch = new AccountBatch(1);

    batch.add();
    batch.setId(idMapper.toBytes(account.accountId()));
    batch.setLedger(ledger);
    batch.setCode(account.code());
    batch.setUserData64(account.userData64());
    batch.setFlags(toAccountFlags(account.flags()));

    return batch;
  }

  @Override
  public IdBatch toIdBatch(UUID id) {
    return new IdBatch(idMapper.toBytes(id));
  }

  @Override
  public List<CreateAccountStatus> toAccountStatuses(CreateAccountResultBatch results) {
    List<CreateAccountStatus> statuses = new ArrayList<>(results.getLength());

    results.beforeFirst();
    while (results.next()) {
      statuses.add(results.getStatus());
    }

    return statuses;
  }

  @Override
  public AccountCreation toAccountCreation(UUID accountId, AccountCreationStatus status) {
    return new AccountCreation(accountId, status);
  }

  @Override
  public Optional<Balance> toBalance(AccountBatch accounts) {
    accounts.beforeFirst();

    if (!accounts.next()) {
      return Optional.empty();
    }

    return Optional.of(
        toBalance(
            idMapper.toUuid(accounts.getId()),
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

  @Override
  public int toAccountFlags(Set<AccountFlag> flags) {
    int bits = AccountFlags.NONE;

    for (AccountFlag flag : flags) {
      bits |= toAccountFlag(flag);
    }

    return bits;
  }

  private static int toAccountFlag(AccountFlag flag) {
    return switch (flag) {
      case DEBITS_MUST_NOT_EXCEED_CREDITS -> AccountFlags.DEBITS_MUST_NOT_EXCEED_CREDITS;
      case CREDITS_MUST_NOT_EXCEED_DEBITS -> AccountFlags.CREDITS_MUST_NOT_EXCEED_DEBITS;
      case HISTORY -> AccountFlags.HISTORY;
    };
  }
}
