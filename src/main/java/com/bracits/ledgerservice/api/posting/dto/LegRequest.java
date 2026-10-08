package com.bracits.ledgerservice.api.posting.dto;

import com.bracits.ledgerservice.domain.constant.DomainConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * One leg of a posting request.
 *
 * @param debit  account debited
 * @param credit account credited
 * @param amount minor units, &gt; 0
 * @param code   transfer code, 1..65535
 */
public record LegRequest(
    @NotNull UUID debit,
    @NotNull UUID credit,
    @NotNull @Positive Long amount,
    @NotNull @Min(DomainConstants.MIN_CODE) @Max(DomainConstants.MAX_CODE) Integer code) {

}
