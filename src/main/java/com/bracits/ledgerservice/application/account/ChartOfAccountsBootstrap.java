package com.bracits.ledgerservice.application.account;

import com.bracits.ledgerservice.config.AccountsProperties;
import com.bracits.ledgerservice.config.ConfigConstants;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import com.bracits.ledgerservice.domain.account.SystemAccount;
import com.bracits.ledgerservice.port.out.LedgerStore;
import com.bracits.ledgerservice.port.out.LedgerUnavailableException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * Creates the fixed system accounts at start-up (spec 6.2, D27). {@code CREATED} and {@code EXISTS}
 * are fine; {@code CONFLICT} fails start-up. While the ledger is unavailable it retries every
 * {@code bootstrapRetryInterval} until {@code bootstrapTimeout}, then fails start-up.
 */
@Component
@ConditionalOnBooleanProperty(name = ConfigConstants.ACCOUNTS_BOOTSTRAP_PROPERTY, matchIfMissing = true)
public final class ChartOfAccountsBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ChartOfAccountsBootstrap.class);

  private static final String LOG_ACCOUNT_READY = "System account {} ({}): {}";
  private static final String LOG_RETRY = "Ledger unavailable while creating system account {}; retrying in {}";
  private static final String MSG_CONFLICT =
      "System account %s (%s) already exists with different fields";
  private static final String MSG_TIMEOUT = "Ledger unavailable for %s while creating system account %s";
  private static final String MSG_INTERRUPTED = "Interrupted while creating system account %s";

  private final LedgerStore ledgerStore;
  private final Duration timeout;
  private final Duration retryInterval;

  public ChartOfAccountsBootstrap(LedgerStore ledgerStore, AccountsProperties properties) {
    this.ledgerStore = ledgerStore;
    this.timeout = properties.bootstrapTimeout();
    this.retryInterval = properties.bootstrapRetryInterval();
  }

  @Override
  public void run(ApplicationArguments args) {
    long deadline = System.nanoTime() + timeout.toNanos();
    for (SystemAccount systemAccount : SystemAccount.values()) {
      create(systemAccount, deadline);
    }
  }

  private void create(SystemAccount systemAccount, long deadline) {
    while (true) {
      try {
        AccountCreation creation = ledgerStore.createAccount(systemAccount.toAccount());
        switch (creation.status()) {
          case CREATED, EXISTS ->
              log.info(LOG_ACCOUNT_READY, systemAccount, systemAccount.id(), creation.status());
          case CONFLICT ->
              throw new IllegalStateException(
                  MSG_CONFLICT.formatted(systemAccount, systemAccount.id()));
        }
        return;
      } catch (LedgerUnavailableException e) {
        if (System.nanoTime() - deadline >= 0) {
          throw new IllegalStateException(MSG_TIMEOUT.formatted(timeout, systemAccount), e);
        }
        log.warn(LOG_RETRY, systemAccount, retryInterval);
        pause(systemAccount);
      }
    }
  }

  private void pause(SystemAccount systemAccount) {
    try {
      Thread.sleep(retryInterval);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(MSG_INTERRUPTED.formatted(systemAccount), e);
    }
  }
}
