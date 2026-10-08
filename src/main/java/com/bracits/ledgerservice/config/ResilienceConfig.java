package com.bracits.ledgerservice.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Enables {@code @ConcurrencyLimit} (bulkhead on the posting path) and binds the property records.
 *
 * <p>{@code proxyTargetClass = true}: the resilience post-processor builds its own proxy and,
 * unlike
 * Spring Boot's AOP auto-configuration, defaults to a JDK interface proxy. That proxy would invoke
 * the interface method, which carries no {@code @ConcurrencyLimit} (it is on the implementation),
 * and fail with "No @ConcurrencyLimit annotation found". A CGLIB class proxy sees the annotated
 * implementation method.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods(proxyTargetClass = true)
@ConfigurationPropertiesScan
public final class ResilienceConfig {

}
