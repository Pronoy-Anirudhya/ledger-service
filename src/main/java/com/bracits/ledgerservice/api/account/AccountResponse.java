package com.bracits.ledgerservice.api.account;

import com.bracits.ledgerservice.domain.account.AccountCreationStatus;
import java.util.UUID;

/**
 * 201 / 200 body of {@code POST /internal/v1/accounts}.
 *
 * @param accountId the account
 * @param status CREATED or EXISTS
 */
public record AccountResponse(UUID accountId, AccountCreationStatus status) {}
