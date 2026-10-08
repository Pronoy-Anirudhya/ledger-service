package com.bracits.ledgerservice.adapter.out.tigerbeetle.mapper;

import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.port.out.exception.LedgerErrorException;
import com.tigerbeetle.CreateAccountStatus;
import java.util.List;

/**
 * Maps TigerBeetle 0.17 account result statuses to account creation statuses.
 */
public interface AccountResultMapper {

  /**
   * Maps the result of creating one account.
   *
   * @throws LedgerErrorException if the count is not 1 or the status is unexpected
   */
  AccountCreationStatus mapAccount(List<CreateAccountStatus> statuses);

  /**
   * Maps one account status.
   *
   * @throws LedgerErrorException if the status is not Created, Exists or ExistsWithDifferent*
   */
  AccountCreationStatus mapAccount(CreateAccountStatus status);
}
