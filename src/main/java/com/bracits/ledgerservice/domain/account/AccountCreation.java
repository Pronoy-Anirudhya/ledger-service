package com.bracits.ledgerservice.domain.account;

import java.util.UUID;

/**
 * Result of creating an account.
 *
 * @param accountId the account
 * @param status created, already existed, or conflict
 */
public record AccountCreation(UUID accountId, AccountCreationStatus status) {}
