package com.bracits.ledgerservice.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/** Enables {@code @ConcurrencyLimit} (bulkhead on the posting path) and binds the property records. */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
@ConfigurationPropertiesScan
public class ResilienceConfig {}
