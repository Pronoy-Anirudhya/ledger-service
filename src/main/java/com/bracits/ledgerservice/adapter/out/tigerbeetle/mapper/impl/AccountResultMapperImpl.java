package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.impl;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.constant.TigerBeetleConstants;
import com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper.AccountResultMapper;
import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.tigerbeetle.CreateAccountStatus;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link AccountResultMapper} for TigerBeetle 0.17 account statuses.
 */
@Component
public final class AccountResultMapperImpl implements AccountResultMapper {

  @Override
  public AccountCreationStatus mapAccount(List<CreateAccountStatus> statuses) {
    if (statuses.size() != 1) {
      throw new LedgerErrorException(
          TigerBeetleConstants.MSG_ACCOUNT_RESULT_COUNT.formatted(statuses.size()));
    }

    return mapAccount(statuses.getFirst());
  }

  @Override
  public AccountCreationStatus mapAccount(CreateAccountStatus status) {
    return switch (status) {
      case Created -> AccountCreationStatus.CREATED;
      case Exists -> AccountCreationStatus.EXISTS;
      case ExistsWithDifferentFlags,
           ExistsWithDifferentUserData128,
           ExistsWithDifferentUserData64,
           ExistsWithDifferentUserData32,
           ExistsWithDifferentLedger,
           ExistsWithDifferentCode -> AccountCreationStatus.CONFLICT;
      default -> throw new LedgerErrorException(
          TigerBeetleConstants.MSG_ACCOUNT_STATUS_UNEXPECTED.formatted(status));
    };
  }
}
