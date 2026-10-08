package com.bracits.ledgerservice.config.properties;

import com.bracits.ledgerservice.config.constant.ConfigConstants;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chart-of-accounts settings ({@code poc.accounts.*}).
 *
 * @param bootstrap              create the fixed system accounts at start-up
 * @param bootstrapTimeout       how long to keep retrying while TigerBeetle comes up
 * @param bootstrapRetryInterval pause between bootstrap attempts
 */
@ConfigurationProperties(ConfigConstants.ACCOUNTS_PREFIX)
public record AccountsProperties(
    @DefaultValue(ConfigConstants.DEFAULT_BOOTSTRAP) boolean bootstrap,
    @DefaultValue(ConfigConstants.DEFAULT_BOOTSTRAP_TIMEOUT) Duration bootstrapTimeout,
    @DefaultValue(ConfigConstants.DEFAULT_BOOTSTRAP_RETRY_INTERVAL)
    Duration bootstrapRetryInterval) {

}
