package com.bracits.ledgerservice.api.account.dto;

import com.bracits.ledgerservice.domain.account.enums.AccountFlag;
import com.bracits.ledgerservice.domain.constant.DomainConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/**
 * {@code POST /internal/v1/accounts} body.
 *
 * @param accountId  non-zero 128-bit id
 * @param code       account code, 1..65535
 * @param flags      optional account flags (default none)
 * @param userData64 optional back-reference such as the wallet id (default 0)
 */
public record CreateAccountRequest(
    @NotNull UUID accountId,
    @NotNull @Min(DomainConstants.MIN_CODE) @Max(DomainConstants.MAX_CODE) Integer code,
    Set<@NotNull AccountFlag> flags,
    Long userData64) {

}
