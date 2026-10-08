package com.bracits.ledgerservice.adapter.out.tigerbeetle.model;

import com.tigerbeetle.CreateTransferStatus;

/**
 * Result of one leg (one event) of a {@code create_transfers} request.
 *
 * @param status    TigerBeetle status
 * @param timestamp TigerBeetle timestamp of the transfer (new or original)
 */
public record LegResult(CreateTransferStatus status, long timestamp) {

}
