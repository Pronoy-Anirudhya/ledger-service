package com.bracits.ledgerservice.application.account.bootstrap;

import com.bracits.ledgerservice.application.constant.ApplicationConstants;
import com.bracits.ledgerservice.config.constant.ConfigConstants;
import com.bracits.ledgerservice.config.properties.AccountsProperties;
import com.bracits.ledgerservice.domain.account.enums.SystemAccount;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.port.out.LedgerStore;
import com.bracits.ledgerservice.port.out.exception.LedgerUnavailableException;
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
@ConditionalOnBooleanProperty(
    name = ConfigConstants.ACCOUNTS_BOOTSTRAP_PROPERTY,
    matchIfMissing = true)
public final class ChartOfAccountsBootstrap implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(ChartOfAccountsBootstrap.class);

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
          case CREATED, EXISTS -> LOG.info(
              ApplicationConstants.LOG_SYSTEM_ACCOUNT_READY,
              systemAccount,
              systemAccount.id(),
              creation.status());
          case CONFLICT -> throw new IllegalStateException(
              ApplicationConstants.MSG_SYSTEM_ACCOUNT_CONFLICT.formatted(
                  systemAccount, systemAccount.id()));
        }

        return;
      } catch (LedgerUnavailableException e) {
        if (System.nanoTime() - deadline >= 0) {
          throw new IllegalStateException(
              ApplicationConstants.MSG_SYSTEM_ACCOUNT_TIMEOUT.formatted(timeout, systemAccount),
              e);
        }

        LOG.warn(ApplicationConstants.LOG_SYSTEM_ACCOUNT_RETRY, systemAccount, retryInterval);
        pause(systemAccount);
      }
    }
  }

  private void pause(SystemAccount systemAccount) {
    try {
      Thread.sleep(retryInterval);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          ApplicationConstants.MSG_SYSTEM_ACCOUNT_INTERRUPTED.formatted(systemAccount), e);
    }
  }
}
