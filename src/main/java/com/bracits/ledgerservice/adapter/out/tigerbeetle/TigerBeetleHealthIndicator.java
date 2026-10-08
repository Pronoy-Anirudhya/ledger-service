package com.bracits.ledgerservice.adapter.out.tigerbeetle;

import com.bracits.ledgerservice.adapter.out.tigerbeetle.FencedClientHolder.Operation;
import com.bracits.ledgerservice.domain.account.SystemAccount;
import com.bracits.ledgerservice.port.out.LedgerUnavailableException;
import com.tigerbeetle.IdBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness check (D28): UP if looking up the e-money issuance account completes within the request
 * deadline. The bean name makes the contributor name {@code tigerbeetle}.
 */
@Component(TigerBeetleConstants.HEALTH_INDICATOR_BEAN_NAME)
public final class TigerBeetleHealthIndicator implements HealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(TigerBeetleHealthIndicator.class);

  private final FencedClientHolder clientHolder;
  private final TigerBeetleMapper mapper;

  public TigerBeetleHealthIndicator(FencedClientHolder clientHolder, TigerBeetleMapper mapper) {
    this.clientHolder = clientHolder;
    this.mapper = mapper;
  }

  @Override
  public Health health() {
    IdBatch ids = mapper.toIdBatch(SystemAccount.EMONEY_ISSUANCE.id());
    try {
      clientHolder.call(Operation.HEALTH_CHECK, client -> client.lookupAccountsAsync(ids));
      return Health.up().build();
    } catch (LedgerUnavailableException e) {
      log.warn(TigerBeetleConstants.LOG_HEALTH_DOWN, e.getMessage());
      return Health.down().withDetail(TigerBeetleConstants.HEALTH_DETAIL_ERROR, e.getMessage()).build();
    }
  }
}
