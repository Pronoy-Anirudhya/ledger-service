package com.bracits.ledgerservice.api.account.dto;

import java.util.UUID;

/**
 * {@code GET /internal/v1/accounts/{accountId}/balance} body, all in minor units.
 *
 * @param accountId      the account
 * @param debitsPosted   posted debits
 * @param creditsPosted  posted credits
 * @param debitsPending  pending debits
 * @param creditsPending pending credits
 * @param available      credits posted − debits posted − debits pending
 */
public record BalanceResponse(
    UUID accountId,
    long debitsPosted,
    long creditsPosted,
    long debitsPending,
    long creditsPending,
    long available) {

}
