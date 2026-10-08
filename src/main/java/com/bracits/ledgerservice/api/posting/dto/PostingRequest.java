package com.bracits.ledgerservice.api.posting.dto;

import com.bracits.ledgerservice.domain.constant.DomainConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /internal/v1/postings} body.
 *
 * @param postingId  128-bit id with low byte 0
 * @param product    {@code user_data_32} (e.g. 1 = SEND_MONEY)
 * @param userData64 {@code user_data_64} (e.g. sender wallet id)
 * @param legs       1..8 legs
 */
public record PostingRequest(
    @NotNull UUID postingId,
    @NotNull @PositiveOrZero Integer product,
    @NotNull Long userData64,
    @NotNull @Size(min = DomainConstants.MIN_LEGS, max = DomainConstants.MAX_LEGS)
    List<@NotNull @Valid LegRequest> legs) {

}
