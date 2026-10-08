package com.bracits.ledgerservice.api.funding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * {@code POST /internal/v1/fundings} body (test profile): move {@code amount} from the e-money
 * issuance account to {@code accountId}.
 *
 * @param fundingId 128-bit id with low byte 0 (idempotency key, like a postingId)
 * @param accountId account credited
 * @param amount    minor units, &gt; 0
 */
public record FundingRequest(@NotNull UUID fundingId, @NotNull UUID accountId,
                             @NotNull @Positive Long amount) {

}
