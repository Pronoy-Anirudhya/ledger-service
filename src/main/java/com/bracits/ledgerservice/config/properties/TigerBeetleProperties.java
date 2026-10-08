package com.bracits.ledgerservice.config.properties;

import com.bracits.ledgerservice.config.constant.ConfigConstants;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * TigerBeetle client settings ({@code poc.tigerbeetle.*}).
 *
 * @param clusterId       cluster id
 * @param addresses       replica addresses, {@code host:port}; hostnames are resolved to IPs
 * @param requestDeadline {@code future.get} deadline per request (800 ms)
 * @param ledger          ledger number of every account and transfer (1 = BDT)
 */
@ConfigurationProperties(ConfigConstants.TIGERBEETLE_PREFIX)
public record TigerBeetleProperties(
    @DefaultValue(ConfigConstants.DEFAULT_CLUSTER_ID) long clusterId,
    @DefaultValue(ConfigConstants.DEFAULT_ADDRESSES) List<String> addresses,
    @DefaultValue(ConfigConstants.DEFAULT_REQUEST_DEADLINE) Duration requestDeadline,
    @DefaultValue(ConfigConstants.DEFAULT_LEDGER) int ledger) {

}
