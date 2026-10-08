package com.bracits.ledgerservice.config.properties;

import com.bracits.ledgerservice.config.constant.ConfigConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Posting settings ({@code poc.posting.*}).
 *
 * @param maxLegs          maximum legs per posting (at most 8)
 * @param concurrencyLimit concurrent postings per instance before shedding with 503
 */
@ConfigurationProperties(ConfigConstants.POSTING_PREFIX)
public record PostingProperties(
    @DefaultValue(ConfigConstants.DEFAULT_MAX_LEGS) int maxLegs,
    @DefaultValue(ConfigConstants.DEFAULT_CONCURRENCY_LIMIT) int concurrencyLimit) {

}
